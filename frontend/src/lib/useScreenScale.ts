import { useEffect } from 'react';

/** The screen size the sign-in screens were designed at. */
const DESIGN_WIDTH = 1440;
const DESIGN_HEIGHT = 900;
/** Below this width only the form card shows (same as Tailwind's `lg` breakpoint). */
const SPLIT_SCREEN_MIN_WIDTH = 1024;
/** Height the card needs on a phone at normal size. */
const PHONE_DESIGN_HEIGHT = 780;

/**
 * Makes the whole page grow or shrink with the real screen size.
 *
 * How: it measures the window (on load and on every resize) and sets the root
 * font size. Tailwind sizes (text, padding, widths, icons…) are in `rem` = a
 * multiple of that font size, so one number scales everything together.
 * Example: a 1920×1080 screen → scale 1.2 → 1rem = 19.2px; 1366×768 → 0.85 → 13.7px.
 *
 * Media queries (`lg:` etc.) are not affected: in media queries `rem` always
 * means the browser's default 16px.
 */
export function useScreenScale() {
  useEffect(() => {
    function update() {
      const width = window.innerWidth;
      const height = window.innerHeight;
      const scale = width >= SPLIT_SCREEN_MIN_WIDTH
        // Split screen: fit both the width and the height of the design.
        ? Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        // Phone/tablet (card only): never bigger than normal, smaller only on short screens.
        : Math.min(1, height / PHONE_DESIGN_HEIGHT);
      const limited = Math.min(Math.max(scale, 0.7), 1.5); // stay readable, never huge
      document.documentElement.style.fontSize = `${16 * limited}px`;
    }

    update();
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('resize', update);
      document.documentElement.style.fontSize = ''; // other pages get the normal size back
    };
  }, []);
}
