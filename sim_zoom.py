"""
Double-tap zoom-in simulation.
Usage: python sim_zoom.py
"""
import math


def get_max_offsets(scale, fitted_w, fitted_h, base_scale, container_w, container_h):
    effective = scale * base_scale
    mx = max(0.0, fitted_w * effective - container_w) / 2.0
    my = max(0.0, fitted_h * effective - container_h) / 2.0
    return mx, my


def spring_dynamics(prev_val, target, velocity, dt):
    stiffness = 200.0
    damping = 2.0 * math.sqrt(stiffness) * 1.0
    force = stiffness * (target - prev_val)
    acceleration = force - damping * velocity
    return prev_val + (velocity + acceleration * dt) * dt, velocity + acceleration * dt


def simulate(container_w, container_h, image_w, image_h, tap_x, tap_y, label=""):
    image_aspect = image_w / image_h
    container_aspect = container_w / container_h
    if image_aspect > container_aspect:
        fitted_w = container_w
        fitted_h = container_w / image_aspect
    else:
        fitted_w = container_h * image_aspect
        fitted_h = container_h
    s_h = container_h / fitted_h if fitted_h > 0 else 1.0
    s_w = container_w / fitted_w if fitted_w > 0 else 1.0
    base_scale = max(0.1, min(1.0, min(s_h, s_w)))

    cx = container_w / 2.0
    cy = container_h / 2.0
    tap_rel_x = tap_x - cx
    tap_rel_y = tap_y - cy

    s0 = 1.0
    oX = 0.0
    oY = 0.0
    start_s = s0 * base_scale
    img_tap_x = (tap_rel_x - oX) / start_s if start_s != 0 else 0.0
    img_tap_y = (tap_rel_y - oY) / start_s if start_s != 0 else 0.0

    TARGET = 3.0
    DT = 0.016

    scale = s0
    velocity = 0.0
    frame = 0

    print(f"\n=== {label} ===")
    print(f"container={container_w:.0f}x{container_h:.0f} image={image_w:.0f}x{image_h:.0f} aspect={image_aspect:.2f}")
    print(f"fitted={fitted_w:.0f}x{fitted_h:.0f} baseScale={base_scale:.3f}")
    print(f"center=({cx:.0f},{cy:.0f}) tap=({tap_x:.0f},{tap_y:.0f}) tapRel=({tap_rel_x:.0f},{tap_rel_y:.0f})")
    print(f"imgTap=({img_tap_x:.1f},{img_tap_y:.1f})")
    print(f"maxOffsets at 3x: maxX={(fitted_w*3*base_scale-container_w)/2:.1f} maxY={(fitted_h*3*base_scale-container_h)/2:.1f}")
    hdr = f"{'f':>3s} {'r':>5s} {'ux':>8s} {'uy':>8s} {'mx':>8s} {'my':>8s} {'px':>8s} {'py':>8s} {'ox':>8s} {'oy':>8s} pivot"
    print(hdr)
    print("-" * len(hdr))

    prev_x_self = False
    prev_y_self = False
    issues = []

    while True:
        for _ in range(4):
            scale, velocity = spring_dynamics(scale, TARGET, velocity, DT / 4)

        r = scale / s0
        cur_s = scale * base_scale
        maxX, maxY = get_max_offsets(scale, fitted_w, fitted_h, base_scale, container_w, container_h)

        ux = oX * r + tap_rel_x * (1.0 - r)
        uy = oY * r + tap_rel_y * (1.0 - r)

        px = img_tap_x if -maxX <= ux <= maxX else 0.0
        py = img_tap_y if -maxY <= uy <= maxY else 0.0
        off_x = max(-maxX, min(maxX, tap_rel_x - px * cur_s))
        off_y = max(-maxY, min(maxY, tap_rel_y - py * cur_s))

        peff_x = (tap_rel_x - off_x) / cur_s if cur_s != 0 else 0.0
        peff_y = (tap_rel_y - off_y) / cur_s if cur_s != 0 else 0.0

        x_self = abs(peff_x - img_tap_x) < 1e-6
        y_self = abs(peff_y - img_tap_y) < 1e-6
        x_mid = abs(peff_x) < 1e-6
        y_mid = abs(peff_y) < 1e-6

        if x_self and y_self:
            pv = "itself"
        elif x_mid and y_mid:
            pv = "midline"
        elif x_mid and y_self:
            pv = "Xmid,Yself"
        elif x_self and y_mid:
            pv = "Xself,Ymid"
        elif (not x_self and not x_mid) or (not y_self and not y_mid):
            pv = "other"
        else:
            pv = "mixed"

        if prev_x_self and not x_self:
            issues.append(f"  f{frame}: X regressed from self!")
        if prev_y_self and not y_self:
            issues.append(f"  f{frame}: Y regressed from self!")
        if pv == "other":
            issues.append(f"  f{frame}: pivot=other px={px:.0f} py={py:.0f} peff=({peff_x:.1f},{peff_y:.1f})")
        prev_x_self = x_self
        prev_y_self = y_self

        print(f"{frame:3d} {r:5.3f} {ux:8.1f} {uy:8.1f} {maxX:8.1f} {maxY:8.1f} "
              f"{px:8.1f} {py:8.1f} {off_x:8.1f} {off_y:8.1f} {pv}")

        frame += 1
        if abs(scale - TARGET) < 0.001 and abs(velocity) < 0.1:
            break
        if frame > 300:
            break

    if issues:
        print(f"\n  ISSUES ({len(issues)}):")
        for i in issues:
            print(i)
    else:
        print("\n  OK: pivot satisfies constraints.")

    # Summarize
    final_r = TARGET / s0
    final_ux = oX * final_r + tap_rel_x * (1.0 - final_r)
    final_uy = oY * final_r + tap_rel_y * (1.0 - final_r)
    final_maxX, final_maxY = get_max_offsets(TARGET, fitted_w, fitted_h, base_scale, container_w, container_h)
    print(f"\n  FINAL state at 3x: ux={final_ux:.1f} uy={final_uy:.1f} maxX={final_maxX:.1f} maxY={final_maxY:.1f}")
    print(f"    ux in bounds: {-final_maxX <= final_ux <= final_maxX}")
    print(f"    uy in bounds: {-final_maxY <= final_uy <= final_maxY}")
    print(f"    |tapRel| vs max: |tapRelX|={abs(tap_rel_x):.1f} vs maxX={final_maxX:.1f} = {abs(tap_rel_x) <= final_maxX}")
    print(f"    |tapRel| vs max: |tapRelY|={abs(tap_rel_y):.1f} vs maxY={final_maxY:.1f} = {abs(tap_rel_y) <= final_maxY}")


if __name__ == "__main__":
    # User's phone: 1920x3840 portrait, image 1600x900 landscape
    CONTAINER_W = 1920.0
    CONTAINER_H = 3840.0
    IMAGE_W = 1600.0
    IMAGE_H = 900.0

    CASES = [
        ("center",        960, 1920),
        ("top-left-1/4",  480,  960),
        ("top-left-1/8",  240,  480),
        ("near-topleft",  100,  200),
    ]

    for name, tx, ty in CASES:
        simulate(CONTAINER_W, CONTAINER_H, IMAGE_W, IMAGE_H, tx, ty, name)
