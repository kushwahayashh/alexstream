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
    const s = this.targets.get(el) || {};
    s.x = el.scrollLeft;
    s.y = el.scrollTop;
    s.tx = x != null ? x : (s.tx ?? el.scrollLeft);
    s.ty = y != null ? y : (s.ty ?? el.scrollTop);
    this.targets.set(el, s);
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
