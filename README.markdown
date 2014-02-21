Android RenderScript QuadCurve Test
===================================

To test renderscript's capability.
At first, I wanted to implement `Path.quadTo()` without Canvas, Path in various ways.

- Circle Method : draws many overlapped circles. It seems inefficient but easy.
  and could be developed to cubicTo().
  This method nothing special so it can be implemented with existig skia api as well.
  good for variant radius and curves overlap which is used in finger touch.

- Distance method : draws each pixel based on distance from the curve.
  To find closest point from curve to each pixel, I had to use cubic function.
  because distance dx^2+dy^2 is quartic function.
  Good for color variant and maybe outline effect.
  Bad for radius variant because it brings fractional function. (dx^2+dy^2)/r^2
  I don't know about finding minimum for fractional function...(exact or numerical)