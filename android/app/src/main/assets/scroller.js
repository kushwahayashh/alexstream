/* ───────────────────────── Smooth scroller ─────────────────────────
   A small lerp-based scroller that gives the home rows/columns the smooth
   ALEX-style glide. One shared instance animates any number of elements.  */

class SmoothScroller {
  constructor() {
    this.targets = new Map();
    this.running = false;
  }

  scrollTo(el, x, y) {
    if (!el) return;
    if (!this.targets.has(el)) {
      this.targets.set(el, {
        x: el.scrollLeft,
        y: el.scrollTop,
        tx: x != null ? x : el.scrollLeft,
        ty: y != null ? y : el.scrollTop,
      });
    } else {
      const s = this.targets.get(el);
      if (x != null) s.tx = x;
      if (y != null) s.ty = y;
    }
    if (!this.running) {
      this.running = true;
      requestAnimationFrame(() => this.tick());
    }
  }

  tick() {
    let active = false;
    this.targets.forEach((s, el) => {
      const dx = s.tx - s.x;
      const dy = s.ty - s.y;
      if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) {
        s.x += dx * 0.22;
        s.y += dy * 0.22;
        el.scrollLeft = s.x;
        el.scrollTop = s.y;
        active = true;
      } else {
        el.scrollLeft = s.tx;
        el.scrollTop = s.ty;
        this.targets.delete(el);
      }
    });
    if (active) requestAnimationFrame(() => this.tick());
    else this.running = false;
  }
}

export const scroller = new SmoothScroller();
