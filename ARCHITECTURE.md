# How Agora Works — A Complete Guide

## 1. Architecture at a Glance

```
┌─────────────────────────────────────────────────┐
│  UI Layer (Compose)                              │
│  ChatApp → MessageList → MessageItem             │
│          → ChatBottomBar                         │
│          → SettingsScreen (multi-page)            │
├─────────────────────────────────────────────────┤
│  ViewModel Layer                                 │
│  ChatViewModel (state, orchestration)            │
│  GenerationManager (LLM calls, tool loops)       │
├─────────────────────────────────────────────────┤
│  API Layer (8 providers)                         │
│  LlmProvider interface → Flow<StreamEvent>       │
│  BaseOpenAiProvider (template for 4 providers)   │
│  LocalProvider + LlamaChatEngine (on-device)     │
├─────────────────────────────────────────────────┤
│  Data Layer                                      │
│  Room DB (conversations + messages + embeddings) │
│  DataStore (settings, API keys, model list)      │
│  Filesystem (memory .md files, GGUF models)      │
├─────────────────────────────────────────────────┤
│  Native Layer (JNI via CMake)                    │
│  llama_chat_jni.cpp (chat generation)            │
│  llama_jni.cpp (embeddings)                      │
│  llama.cpp (Git submodule under thirdparty/)     │
└─────────────────────────────────────────────────┘
```

**MVVM with a single ViewModel.** `ChatViewModel` owns all app state. It's created via `ChatViewModelFactory` which injects `ChatDao`, `SettingsManager`, and `MemoryManager`. The UI observes `StateFlow`s.

---

## 2. Data Layer

### 2a. Room Database (`data/local/ChatDatabase.kt`)

Three tables at version 11, with 8 incremental migrations (v2→v3 through v10→v11):

#### `conversations` table (`ChatEntity`)

| Column | Type | Purpose |
|---|---|---|
| `id` | String (PK) | UUID |
| `title` | String | Display name |
| `lastUpdated` | Long | Sort order |
| `selectedBranchesJson` | String? | JSON map of `parentId → chosenChildId` for branch selection |
| `systemPromptId` | String? | Which system prompt is active for this conversation |
| `modelId` | String? | Which model is active |

#### `messages` table (`MessageEntity`)

| Column | Type | Purpose |
|---|---|---|
| `id` | String (PK) | UUID (with prefixes `tool_`/`result_` for synthetic messages) |
| `conversationId` | String (FK) | Parent conversation (CASCADE on delete) |
| `parentId` | String? | Parent message — forms the **tree** structure |
| `text` | String | Message body |
| `images` | List\<String\> | Local file paths to processed images |
| `thoughts` | String? | Aggregated thinking/reasoning text |
| `thoughtTitle` | String? | Title extracted from thinking (e.g. `**Analysis**`) |
| `tokenCount` | Int | Total tokens used (from API usage metadata) |
| `status` | Enum | SENDING → THINKING → SUCCESS / STOPPED / ERROR |
| `participant` | Enum | USER or MODEL |
| `timestamp` | Long | Creation time |
| `thoughtTimeMs` | Long? | How long the model spent thinking |
| `modelName` | String? | Which model generated this |
| `toolCallJson` | String? | JSON of `MessageSegment` list (thought + tool segments) |

#### `embeddings` table (`EmbeddingEntity`)

| Column | Type | Purpose |
|---|---|---|
| `id` | Long (PK, auto) | Auto-increment |
| `messageId` | String | FK to messages |
| `modelId` | String | Which embedding model produced this |
| `embedding` | ByteArray | Big-endian float32 vector |
| `chunkText` | String | First 500 chars of source text (preview) |
| `dimension` | Int | Embedding vector dimension |

Unique constraint on `(messageId, modelId)` — one embedding per message per model.

Messages form a **tree**, not a linear list. Each message has a `parentId` pointer. When you edit or regenerate, a new sibling is created under the same parent. The "selected path" is determined by `_selectedChildren` which maps `parentId → chosenChildId`.

`MessageConverters` handles type conversion for `Participant`, `MessageStatus`, `List<String>` (with backward compatibility for old `|||` delimiter format).

### 2b. DataStore Settings (`data/SettingsManager.kt`)

Persists to `settings` DataStore preferences file. Stores:

- `selectedModel` — default model (e.g. `"gemini-1.5-flash"`)
- `availableModels` — JSON map `{ "Google": ["Google:gemini-2.5-flash", ...], "OpenAI": ["OpenAI:gpt-4", ...] }`
- `enabledModels` — which models the user has toggled on
- `modelAliases` — custom display names for models
- `apiKeys` — list of `ApiKeyEntry` (id, name, key, provider)
- `activeApiKeyIds` — which key is selected per provider `{ "Google": "key-uuid", ... }`
- `systemPrompts` — list of `SystemPromptEntry` (id, title, content)
- `activeSystemPromptId` — global default system prompt
- `maxContextWindow` — how many messages to send (default 20)
- `codeExecutionEnabled` / `googleSearchEnabled` / `thinkingEnabled` — bool toggles
- `providerBaseUrls` — custom base URLs per provider (for proxies, Ollama, etc.)
- `titleGenerationEnabled` / `titleGenerationModel` — auto-title settings
- `ragSearchEnabled` / `modelSearchMethod` / `manualSearchMethod` / `ragThreshold` — RAG config
- `embeddingModels` / `activeEmbeddingModelId` — embedding model list and active selection
- `localChatModels` / `activeLocalChatModelId` — local GGUF chat model list and active
- `appLanguage` — UI language override
- `webSearch*` — web search provider/apiKey/baseUrl config

All reads use `Flow`; all writes use `dataStore.edit {}`. The ViewModel calls `.stateIn(viewModelScope)` on each flow to create hot `StateFlow`s.

### 2c. Memory Manager (`data/MemoryManager.kt`)

A file-based "memory" store that the model can manipulate via tool calls:

- **Active memory** (`active_memory.md`) — prepended to every API call's system prompt
- **Saved files** (`memory_db/*.md`) — individual markdown files the model can create, read, edit, delete

Thread-safe via `@Synchronized`. Path traversal protected by canonical path checks.

---

## 3. The ViewModel Layer

### 3a. ChatViewModel (`viewmodel/ChatViewModel.kt`, ~1355 lines)

This is the central orchestrator. Its responsibilities:

**Conversation management:**
- `createNewChat()` — clears state, switches to "new chat" mode
- `selectConversation(id)` — loads a conversation, restoring branch selections from `selectedBranchesJson`
- `deleteConversation(id)` — cascades: deletes embeddings → messages → conversation
- `renameConversation(id, title)`, `generateTitle(conversationId)`

**Message sending (3 entry points):**
- `sendMessage(text, images)` — new message in conversation
- `regenerate(messageId)` — replace a model response with a new sibling
- `editMessage(messageId, newText)` — edit a user message, creating a branch

All three follow the same pattern:
1. Compute IDs, create placeholder model message in `_allMessages`
2. Set `_streamingMessage` = placeholder (UI shows streaming bubble)
3. Launch `GenerationManager.generate()` in `generationScope` (IO dispatcher)
4. Pass callbacks: `onStreamUpdate`, `onLoadingChange`, `onGeneratingIdChange`, `onStreamClear`

**Branch switching:**
- `switchBranch(parentId, direction)` — cycle through sibling messages at the same tree level
- Updates `_selectedChildren` which the `messages` StateFlow reads

**The `messages` StateFlow** is the heart of the app. It `combine`s three flows:
1. `_allMessages` — all messages in the current conversation (from Room DB Flow)
2. `_streamingMessage` — the currently-streaming message (null when idle)
3. `_selectedChildren` — branch selection map

It walks the tree from the root (parentId=null), following `_selectedChildren` to pick which child to follow at each level. The streaming message **overlays** its corresponding DB message. Synthetic `tool_`/`result_` messages are hidden from the display path. Result: a linear list of `ChatMessage` for the UI.

**RAG/Embeddings management:**
- `indexMessageForRag()` — indexes a single message (called by `onMessagePersisted` callback)
- `cacheMessagesForModel()` — bulk-cache all messages for an embedding model
- `semanticSearch()` — delegates to `GenerationManager.semanticSearch()`
- `refreshCacheCounts()` — calculates per-model cached/total counts, clamping orphaned counts
- Orphaned embeddings cleanup on init via `deleteOrphanedEmbeddings()`

**Local chat model management:**
- `addLocalChatModel()`, `deleteLocalChatModel()`, `updateLocalChatModel()`
- `isLocalModelIdTaken()` — uniqueness check for modelId slug
- Auto-syncs local models into `availableModels` and `modelAliases` via a `collect` in init

**Model syncing** (`fetchAvailableModels`): iterates all 8 providers, calls `provider.fetchModels()`, saves results to DataStore. Skips "Local" (managed separately).

### 3b. GenerationManager (`viewmodel/GenerationManager.kt`, ~902 lines)

This is the engine that actually talks to LLMs. Configuration is via two immutable data classes: `GenerationConfig` (provider, model, API key, system prompt, toggles) and `GenerationContext` (memory access, RAG settings, web search config, embedding params).

**`generate()` function** — the main pipeline:

```
1. Build message path from DB (walking parentId chain)
2. If regenerating: trim path to exclude the message being replaced
3. Build memory tools, web search tool, RAG tool from GenerationContext
4. Call provider.generateResponse(currentPath, ProviderConfig) → Flow<StreamEvent>
5. Collect events:
   - TextChunk → accumulate into totalText
   - ThoughtChunk → accumulate into currentThoughtBuf + totalThoughts
   - ToolCallRequest / ToolCallsRequest → execute tool locally, add to segments
   - UsageUpdate → track token count
   - Error → set error status
6. After each event: call onStreamUpdate() with live ChatMessage (throttled to ~500ms)
7. If tool calls were made: enter multi-tool loop (unlimited rounds, bounded by coroutine liveness)
   - Persist tool_ and result_ messages to DB
   - Call provider.generateResponse() again with updated path
8. In finally: persist final message to DB, call onStreamClear(), stop foreground service
```

**Memory tools** (`buildMemoryTools()`): defines 6 tools the model can call:
- `list_memory_files`, `read_memory_file`, `create_memory_file`, `edit_memory_file`, `delete_memory_file`, `update_active_memory`

**Web search tool** (`buildWebSearchTool()`): Brave Search API (requires key) or SearXNG (configurable URL, no key).

**RAG tool** (`buildRagTool()`): `search_conversations` uses semantic (cosine similarity) or keyword search depending on `modelSearchMethod`.

**Image processing** (`processImages()`): Decodes URIs from the photo picker, resizes to max ~1024px, re-compresses to JPEG at 80% quality, saves to internal storage. Handles video frames via `MediaMetadataRetriever`.

**Semantic search** (`semanticSearch()`): Computes query embedding, iterates all stored embeddings for the active model, computes cosine similarity, filters by `ragThreshold`, returns top N results with scores.

---

## 4. The API Provider Layer

### 4a. Interface & Types (`api/LlmProvider.kt`)

```kotlin
interface LlmProvider {
    val name: String
    val defaultBaseUrl: String
    fun generateResponse(messages: List<ChatMessage>, config: ProviderConfig): Flow<StreamEvent>
    suspend fun fetchModels(apiKey: String, baseUrl: String?): List<String>
}
```

**`StreamEvent`** sealed class — the universal output format:
- `TextChunk(text)` — a piece of the model's response
- `ThoughtChunk(thought, title?, signature?)` — a piece of reasoning/thinking
- `ToolCallRequest(id, name, arguments, signature?)` — single tool call
- `ToolCallsRequest(calls)` — multiple tool calls in one response
- `UsageUpdate(tokenCount, thoughtsTokenCount)` — token usage metadata
- `Error(message)` — error

**Key design**: All 8 providers produce the same `Flow<StreamEvent>`, so `GenerationManager` only deals with one event type.

### 4b. BaseOpenAiProvider (Template Method Pattern)

Four providers (OpenAI, DeepSeek, Qwen, OpenRouter) share this base class. It handles:
- Building the `OpenAiChatRequest` with common fields (`model`, `messages`, `stream`, `tools`)
- Opening HTTP connection, writing JSON body
- Reading SSE lines (`data: {...}`), parsing JSON, dispatching to `parseDeltaContent()`
- Accumulating tool calls in `pendingToolCalls` map (deduplicates by index)
- Emitting `StreamEvent.ToolCallRequest` / `ToolCallsRequest` on `finish_reason=tool_calls`
- Emitting `UsageUpdate` when usage data arrives
- Flushing `thinkParser` after the SSE loop ends (fix for buffered `<think>` content)

Subclasses override:
- `parseDeltaContent()` — how to extract text/thought from a delta
- `customizeRequest()` — add provider-specific fields (reasoning, plugins)
- `getExtraHeaders()` — provider-specific HTTP headers
- `transformSystemPrompt()` — modify system prompt before sending

### 4c. Individual Providers

| Provider | Base | Key Differences |
|---|---|---|
| **OpenAI** | BaseOpenAiProvider | `reasoningEffort = "medium"` for o1/o3; parses `reasoningContent`; respects `thinkingEnabled` |
| **DeepSeek** | BaseOpenAiProvider | Parses `reasoningContent`; does NOT respect `thinkingEnabled` (BUG — see §9) |
| **Qwen** | BaseOpenAiProvider | DashScope base URL; parses `reasoningContent`; respects `thinkingEnabled` |
| **OpenRouter** | BaseOpenAiProvider | Timestamp in system prompt; `reasoning` effort field; `reasoningDetails` array; web `plugins`; referer/title headers |
| **Anthropic** | Direct `LlmProvider` | Custom SSE protocol (event:/data: lines); `content_block_start/delta/stop` events; thinking via `budget_tokens`; tool_use/tool_result blocks; images NOT supported (BUG — see §9) |
| **Gemini** | Direct `LlmProvider` | Google's `streamGenerateContent` SSE; inline base64 images; `thought` flag; code execution + Google Search as Google-specific tools; different thinking config for Gemini 2.5 vs 3 |
| **Ollama** | Direct `LlmProvider` | Local server; `/api/chat` endpoint; structured `thinking` field or `<think>` tag fallback; `/api/tags` for model list |
| **Local** | Direct `LlmProvider` | On-device GGUF via llama.cpp JNI; chat template or ChatML fallback; StreamingThinkTagParser for `<think>`; Mutex-guarded engine lifecycle; tool calls unsupported (serialized as text) |

### 4d. Message Conversion (`api/util/MessageConverter.kt`)

`convertToOpenAiMessages()` transforms the internal `ChatMessage` tree into OpenAI-format API messages:
- `tool_` messages → `assistant` role with `tool_calls` array + `reasoning_content` from thought segments
- `result_` messages → `tool` role with `tool_call_id` matching the tool
- Normal messages → text + base64-encoded images
- Tool call IDs are SHA-256 hashes of `toolName:arguments` — deterministic for multi-turn consistency

Anthropic, Gemini, Ollama, and Local each have their own conversion logic inline.

### 4e. HTTP Client (`api/HttpClient.kt`)

Wraps OkHttp for all providers:
- `streamPost(url, body, headers)` → returns `StreamHandle` with `readLine()` and `close()`
- `post(url, body, headers)` → returns response body string or null
- `fetchModels(url, headers)` → GET wrapper for model list endpoints
- 30s connect/read/write timeouts

### 4f. Streaming Think Tag Parser (`api/util/StreamingThinkTagParser.kt`)

A stateful buffer-based parser for `<think>...</think>` tags in streaming content. Used by Ollama (as fallback when no structured thinking field), DeepSeek (via BaseOpenAiProvider flush), LocalProvider.

Features one-exit guard: only honors the first `<think>` block to avoid swallowing literal `<think>` in normal text.

### 4g. Embedding Client (`api/EmbeddingClient.kt`)

OpenAI-compatible embeddings API client:
- `computeEmbedding(text, apiKey, model, baseUrl)` — single embedding
- `computeEmbeddings(texts, apiKey, model, baseUrl)` — batch embedding
- Manual JSON escaping (no library dependency for embedding requests)

---

## 5. The Native Layer (JNI)

### 5a. Build System (`app/src/main/cpp/CMakeLists.txt`)

- llama.cpp built as static library from `thirdparty/llama.cpp` (git submodule)
- JNI wrapper built as shared library `agora_llama`
- C++17, arm64-v8a only, links `llama` + `log`
- GGML_OPENMP forced OFF for Android compatibility

### 5b. Chat JNI (`llama_chat_jni.cpp`, ~370 lines)

Wraps llama.cpp chat generation following official `simple-chat.cpp` best practices:

| JNI Function | Purpose |
|---|---|
| `nativeChatLoadModel` | Loads GGUF, creates context with `n_batch = n_ctx`, sets abort callback |
| `nativeChatGetTemplate` | Returns model's Jinja chat template or null |
| `nativeChatApplyTemplate` | Calls `llama_chat_apply_template()` with model's template, retries on buffer overflow |
| `nativeChatGenerate` | Tokenization, prefill via `llama_batch_get_one`, sampler chain (min_p→top_p→temp→dist), generation loop with cancel check, context space check |
| `nativeChatReset` | Clears KV cache via `llama_memory_clear()` |
| `nativeChatFreeModel` | Frees model and context |
| `nativeChatCancel` | Sets volatile cancel flag checked by abort callback and generation loop |

Sampler chain: `min_p(0.05) → top_p(configurable) → temp(configurable) → dist(seed)`

### 5c. Embedding JNI (`llama_jni.cpp`, ~175 lines)

Wraps llama.cpp embedding inference:

| JNI Function | Purpose |
|---|---|
| `nativeLoadModel` | Loads GGUF with `embeddings=true`, `pooling_type=MEAN`, `n_ctx=512`, `n_batch=512` |
| `nativeFreeModel` | Frees model and context |
| `nativeComputeEmbedding` | Tokenizes, creates batch manually (`llama_batch_init`), calls `llama_encode`/`llama_decode`, returns pooled embedding |
| `nativeGetEmbeddingDim` | Returns `llama_model_n_embd_out()` |

Uses `llama_batch_init` with manual pos/seq_id/logits filling (differs from chat JNI which uses `llama_batch_get_one`).

### 5d. Kotlin JNI Wrappers

**`LlamaEngine`** (embedding, singleton): Provides both single-embedding (`computeEmbedding`) and batch (`computeEmbeddings`) methods. Batch loads the model once, computes all embeddings, frees once — eliminating the per-message load/free overhead during bulk caching.

**`LlamaChatEngine`** (chat, per-instance): Persistent model instance. Streams tokens via `callbackFlow` with `NativeChatCallback`. Uses `awaitClose` for proper cancellation cleanup. Supports cancel, reset context, apply template. Errors are properly propagated as exceptions through the flow.

---

## 6. The UI Layer

### 6a. MainActivity (`MainActivity.kt`)

Entry point. Handles:
- Splash screen setup
- Notification channel creation + permission request
- Database version check (shows error dialog if stored version > current version)
- Creates `MemoryManager`, `SettingsManager`, and `ChatDatabase`
- Sets up the Compose content tree: `AgoraTheme` → `MainNavigation`

### 6b. ChatApp composable

The main screen with:
- **ModalNavigationDrawer** — chat history sidebar with new chat button, conversation list (long-press for rename/delete/generate title), settings button
- **Scaffold** — top app bar (title, menu icon, system prompt selector, new chat button)
- **AnimatedContent** — switches between "New Chat" welcome screen and `MessageList`
- **MessageList** — LazyColumn of chat bubbles
- **ChatBottomBar** — text input, image picker, model selector, tool toggles, send/stop button
- **Full-screen image viewer** — pinch-to-zoom, pan, double-tap, fling, rubber-band physics
- **Settings overlay** — slides in from right with scrim

### 6c. MessageList (`ui/chat/MessageList.kt`)

A `LazyColumn` rendering each visible message as a `MessageItem`. Computes context window boundaries for the "context rollout" visualization (messages outside the context window are dimmed). Manages extra bottom padding for scroll anchoring during streaming.

### 6d. MessageItem (`ui/chat/MessageItem.kt`, ~1000 lines)

Three visual modes:
- **USER**: Right-aligned bubble with text, images, copy/edit/info actions, branch switcher
- **MODEL**: Left-aligned with thought blocks (expandable), tool call blocks (expandable), markdown rendering with inline LaTeX, status indicators (SENDING spinner, THINKING pulsing dots, STOPPED badge, ERROR banner), context rollout dimming
- **ERROR**: Center error banner

Supports inline editing of user messages (which triggers `editMessage()`), branch switching arrows, and streaming content with debounced re-rendering.

### 6e. ChatBottomBar (`ui/chat/ChatBottomBar.kt`)

Input area with:
- Expandable text field with custom scrollbar
- Image picker using `PickMultipleVisualMedia` (Android Photo Picker)
- Model selector dropdown
- Tools menu: Code Execution, Web Search, Thinking toggles
- Send FAB (pulsing when loading) / Stop button

### 6f. Settings Screen (`ui/settings/SettingsScreen.kt`, ~1100 lines)

Three tabs via `HorizontalPager`:
- **General**: Settings list linking to sub-pages (Provider, Models, Prompts, Context, Memory, Search, Web Search, Title Generation, Language)
- **Models**: Not used directly (sub-pages provide model management)
- **Memory**: Active memory editor, saved memories list with CRUD

Sub-pages:
- `SettingsProviderPage` — API key CRUD, base URL per provider, Local GGUF model management
- `SettingsModelsPage` — Default model selector, sync button, expandable per-provider model lists
- `SettingsPromptsPage` — System prompt CRUD
- `SettingsContextPage` — Context window slider, rollout visualization toggle
- `SettingsMemoryPage` — Active memory + saved memory files
- `SettingsSearchPage` — Access toggle, search method (keyword/RAG), embedding model management, RAG threshold
- `SettingsWebSearchPage` — Web search toggle, provider selection (Brave/SearXNG), API key/URL
- `SettingsTitleGenPage` — Auto-title toggle + model selection
- `SettingsLanguagePage` — Language selection with immediate snackbar feedback

---

## 7. Key Data Flows

### Sending a message (end-to-end):

```
1. User types text + attaches images, taps Send
2. ChatBottomBar calls viewModel.sendMessage(text, images)
3. ChatViewModel:
   a. If new chat: creates ChatEntity in Room
   b. Creates user MessageEntity in Room (status=SUCCESS)
   c. Creates placeholder model MessageEntity (status=SENDING)
   d. Sets _streamingMessage = placeholder
   e. Sets _isLoading = true
   f. Launches generationScope { generationManager.generate(...) }
4. GenerationManager:
   a. Builds message path from DB (walking parentId chain)
   b. Builds memory tools, web search tool, RAG tool
   c. Starts foreground service
   d. Calls provider.generateResponse(path, config)
   e. Collects TextChunk, ThoughtChunk, etc.
   f. After each event (throttled ~500ms): onStreamUpdate(updatedMsg) → ViewModel updates _streamingMessage → UI recomposes
   g. If tool calls: executes locally, persists tool_/result_ to DB, loops
   h. In finally: persists final message to DB, onStreamClear() → _streamingMessage = null
5. onMessagePersisted callback triggers indexMessageForRag() if RAG is enabled
6. UI: MessageList observes messages StateFlow, shows streaming bubble with real-time text
```

### Branching (message tree):

```
Root (null)
├── User Msg A (id=1, parentId=null)
│   ├── Model Response X (id=2, parentId=1)     ← selectedChildren[1] = 2
│   └── Model Response Y (id=3, parentId=1)     ← regenerate created this sibling
├── User Msg B (id=4, parentId=2)               ← follows selected path
│   └── Model Response Z (id=5, parentId=4)
```

`_selectedChildren` = `{ "1": "2", "4": "5" }` walks → [1, 2, 4, 5]
Switch branch at parentId=1 → `{ "1": "3", "4": "5" }` walks → [1, 3]

### Tool call flow:

```
1. Model's response includes a tool_call (e.g. "read_memory_file")
2. Provider emits ToolCallRequest(id, "read_memory_file", {"name":"notes.md"})
3. GenerationManager:
   a. executeTool("read_memory_file", args) → calls memoryManager.readFile("notes.md")
   b. Creates MessageSegment(type="tool", toolName=..., toolResult=...)
   c. Adds to segments list (visible in UI as tool call block)
4. After stream ends: tool_ message persisted to DB, result_ message persisted
5. Multi-tool loop: re-calls provider.generateResponse() with updated path including tool results
6. Model sees tool result and continues its response
```

### Embedding lifecycle:

```
CACHE: cacheMessagesForModel() →
  1. If re-cache: uncache model, delete all embeddings for model
  2. Iterate all indexable messages (user/model, non-blank)
  3. Batch process: local models load once and process all messages in one shot;
     remote models batch 64 texts per API call via EmbeddingClient.computeEmbeddings
  4. Upsert embeddings, mark model as cached in DataStore

INDEX: onMessagePersisted →
  indexMessageForRag() → if RAG enabled for model/manual search → compute + upsert single embedding

SEARCH: semanticSearch() →
  1. Compute query embedding
  2. Load all embeddings for active model
  3. Compute cosine similarity, filter by ragThreshold, sort, limit
  4. Fetch corresponding MessageEntity by IDs

DELETE: deleteConversation() →
  deleteEmbeddingsByConversation → deleteMessagesByConversation → deleteConversation

ORPHAN: deleteOrphanedEmbeddings() →
  DELETE embeddings WHERE messageId NOT IN (SELECT id FROM messages)
  Called on app startup + after conversation deletions
```

---

## 8. Key Design Decisions

- **No HTTP library wrapper** — uses OkHttp directly with raw `HttpURLConnection`-style patterns. OkHttp provides connection pooling across all providers.
- **No repository layer** — ViewModel talks directly to DAO and SettingsManager. `GenerationManager` is a step toward separation but isn't a full repository.
- **Single ViewModel** — `ChatViewModel` holds most state (~1355 lines). No per-screen ViewModels.
- **Message tree, not list** — the `parentId` + `_selectedChildren` approach enables branching conversations without data duplication.
- **Tool calls are local** — the model can manipulate memory files, search past conversations (RAG), and search the web (Brave/SearXNG). No code execution sandbox.
- **SSE streaming everywhere** — all providers stream via Server-Sent Events.
- **Model IDs are prefixed** — format is `ProviderName:model-id` (e.g. `OpenAI:gpt-4`). The prefix determines which provider class and API key to use.
- **llama.cpp as git submodule** — under `thirdparty/llama.cpp`, linked via CMake `add_subdirectory`.
- **Separate JNI files for embedding vs chat** — `llama_jni.cpp` for embeddings, `llama_chat_jni.cpp` for chat generation.
- **Mutex-guarded engine lifecycle in LocalProvider** — only one model loaded at a time, swapped when user selects different model.
- **On-device inference runs on IO dispatcher** — not blocking the main thread, with cancel support via volatile flag + abort callback.

## 9. File Index

### API Layer (16 files)
| File | Lines | Purpose |
|---|---|---|
| `api/LlmProvider.kt` | 218 | Interface + StreamEvent + request/response types |
| `api/BaseOpenAiProvider.kt` | 208 | Template for OpenAI-compatible providers |
| `api/OpenAiProvider.kt` | 31 | OpenAI (reasoning_effort, reasoning_content) |
| `api/DeepSeekProvider.kt` | 25 | DeepSeek (reasoningContent) |
| `api/QwenProvider.kt` | 25 | Qwen/DashScope (reasoningContent) |
| `api/OpenRouterProvider.kt` | 62 | OpenRouter (plugins, reasoning, web search) |
| `api/AnthropicProvider.kt` | 412 | Anthropic Claude (SSE events, thinking budget) |
| `api/GeminiProvider.kt` | 494 | Google Gemini (code exec, web search, thinking) |
| `api/OllamaProvider.kt` | 286 | Ollama local server |
| `api/LocalProvider.kt` | 200 | On-device GGUF via llama.cpp |
| `api/LlamaChatEngine.kt` | 143 | JNI wrapper for chat models |
| `api/LlamaEngine.kt` | 47 | JNI wrapper for embedding models |
| `api/EmbeddingClient.kt` | 83 | OpenAI-compatible embeddings API |
| `api/HttpClient.kt` | 60 | OkHttp wrapper |
| `api/util/MessageConverter.kt` | 136 | ChatMessage → OpenAI format converter |
| `api/util/StreamingThinkTagParser.kt` | 76 | Streaming `<think>` tag parser |

### ViewModel Layer (3 files)
| File | Lines | Purpose |
|---|---|---|
| `viewmodel/ChatViewModel.kt` | 1355 | Central ViewModel (all state + orchestration) |
| `viewmodel/GenerationManager.kt` | 902 | Generation engine (streaming, tools, RAG) |
| `viewmodel/ChatViewModelFactory.kt` | ~20 | Manual DI factory |

### Data Layer (6 files)
| File | Lines | Purpose |
|---|---|---|
| `data/local/ChatDatabase.kt` | 256 | Room DB v11, 8 migrations, ChatDao |
| `data/SettingsManager.kt` | 291 | DataStore preferences (all settings) |
| `data/MemoryManager.kt` | 92 | File-based persistent memory |
| `data/EmbeddingModelConfig.kt` | 18 | Embedding model config data class |
| `data/LocalChatModelConfig.kt` | 17 | Local chat model config data class |
| `data/EmbeddingIndexer.kt` | 33 | FloatArray↔ByteArray + cosine similarity |

### Native Layer (3 files)
| File | Lines | Purpose |
|---|---|---|
| `cpp/llama_chat_jni.cpp` | 370 | Chat generation JNI |
| `cpp/llama_jni.cpp` | 175 | Embedding JNI |
| `cpp/CMakeLists.txt` | 19 | CMake build config |

### UI Layer (14 files)
| File | Lines | Purpose |
|---|---|---|
| `MainActivity.kt` | ~600 | Entry point, Compose tree, splash |
| `ui/chat/MessageItem.kt` | ~1000 | Chat bubble composable |
| `ui/chat/MessageList.kt` | ~200 | LazyColumn message list |
| `ui/chat/ChatBottomBar.kt` | ~300 | Input bar + model selector |
| `ui/chat/VideoPlayer.kt` | ~50 | Embedded video player |
| `ui/settings/SettingsScreen.kt` | ~1100 | Main settings screen |
| `ui/settings/SettingsProviderPage.kt` | 637 | Provider config + local models |
| `ui/settings/SettingsModelsPage.kt` | ~200 | Model selection |
| `ui/settings/SettingsPromptsPage.kt` | ~150 | System prompt CRUD |
| `ui/settings/SettingsMemoryPage.kt` | ~200 | Memory management |
| `ui/settings/SettingsSearchPage.kt` | 536 | Search + embedding settings |
| `ui/settings/SettingsWebSearchPage.kt` | 194 | Web search settings |
| `ui/settings/SettingsLanguagePage.kt` | ~100 | Language selection |
| `ui/settings/SettingsContextPage.kt` | ~100 | Context window settings |
| `ui/settings/SettingsTitleGenPage.kt` | ~100 | Title generation settings |
| `ui/components/LatexRenderer.kt` | ~100 | LaTeX math rendering |
| `ui/components/TypewriterText.kt` | ~50 | Streaming text animation |
| `ui/theme/Color.kt` | ~20 | Color definitions |
| `ui/theme/Theme.kt` | ~50 | Agora theme |
| `ui/theme/Type.kt` | ~20 | Typography |

### Utilities (3 files)
| File | Lines | Purpose |
|---|---|---|
| `util/Constants.kt` | 7 | Message prefix constants |
| `util/SearchResultFormatter.kt` | ~30 | Web result formatting |
| `util/SnackbarEvent.kt` | ~10 | Snackbar event type |

### Models (1 file)
| File | Lines | Purpose |
|---|---|---|
| `model/ChatMessage.kt` | 58 | Core data classes |
