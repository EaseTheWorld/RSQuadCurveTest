#pragma version(1)
#pragma rs java_package_name(com.easetheworld.renderscript.curve)

#include "debug.rsh"
#include "util.rsh"

#define USE_NEWTON 0
#define EPSILON 0.0001f

// bezier curve 0 ~ 1
#define T0 0.f
#define T1 1.f

#define BEFORE_RANGE 0.f
#define IN_RANGE 0.5f
#define AFTER_RANGE 1.f

#define TYPE_LINE 0
#define TYPE_CURVE 1

#define PI_2_3 M_PI * 2.f / 3.f

typedef struct {
	// given data
    float x0;
    float y0;
    float x1;
    float y1;
    float x2;
    float y2;
	float4 c0;
	float4 c1;
	float4 c2;
	float r0;
	float r1;
	float r2;
    bool useFirstCap;
    bool useLastCap; // not used. to fill the gap between curves, last cap is always used.
    
   	// precalculated data 
    int type;
   	float x10;
   	float x21;
   	float y10;
   	float y21;
   	float ax;
   	float bx;
   	float cx;
   	float ay;
   	float by;
   	float cy;
    float ax2ay2;
    float bx2by2;
    float axbxayby;
    float tc;
    
    float ar;
    float br;
    float cr;

    float4 ac;
    float4 bc;
    float4 cc;
} QuadCurve;

static QuadCurve curve1;
static QuadCurve curve2;
static QuadCurve *curCurve = NULL;
static QuadCurve *prevCurve = NULL;

static float3 lastPoint;
static float4 lastColor;

float blurRadius = 0.f;

rs_script script;
rs_allocation allocation;

static void fillQuadCurve(QuadCurve *curve, float x0, float y0, float r0, float4 c0, float x1, float y1, float r1, float4 c1, float x2, float y2, float r2, float4 c2, bool useFirstCap) {
	curve->x0 = x0;
	curve->y0 = y0;
	curve->x1 = x1;
	curve->y1 = y1;
	curve->x2 = x2;
	curve->y2 = y2;
	curve->x10 = x1 - x0;
	curve->x21 = x2 - x1;
	curve->y10 = y1 - y0;
	curve->y21 = y2 - y1;
	curve->r0 = r0;
	curve->r1 = r1;
	curve->r2 = r2;
	curve->c0 = c0;
	curve->c1 = c1;
	curve->c2 = c2;
	curve->useFirstCap = useFirstCap;

	// quad curve
    
    if (curve->x21 * curve->y10 == curve->x10 * curve->y21) { // line
    	curve->type = TYPE_LINE;
    	curve->ax = x2 - x0;
    	curve->ay = y2 - y0;
        curve->ax2ay2 = curve->ax * curve->ax + curve->ay * curve->ay;
    } else { // quad curve
    	curve->type = TYPE_CURVE;
		curve->ax = x0 - 2.f * x1 + x2;
		curve->ay = y0 - 2.f * y1 + y2;
        curve->bx = curve->x10;
        curve->by = curve->y10;
        curve->cx = x0;
        curve->cy = y0;
        curve->ax2ay2 = curve->ax * curve->ax + curve->ay * curve->ay;
        curve->bx2by2 = curve->bx * curve->bx + curve->by * curve->by;
        curve->axbxayby = curve->ax * curve->bx + curve->ay * curve->by;
        curve->tc = -curve->axbxayby / curve->ax2ay2;
    }
    
    curve->ar = curve->r0 - 2.f * curve->r1 + curve->r2;
    curve->br = 2.f * (curve->r1 - curve->r0);
    curve->cr = curve->r0;
    
    curve->ac = curve->c0 - 2.f * curve->c1 + curve->c2;
    curve->bc = 2.f * (curve->c1 - curve->c0);
    curve->cc = curve->c0;
}

typedef struct {
	QuadCurve* curve;
	float px;
	float py;
    float f0;
    float f1;
    float df0;
    float df1;
    float axcxaycy2bx2by2; // coeff for t2
    float bxcxbycy; // coeff for t
    float cx;
    float cy;
    float cx2cy2; // coeff for 1
} PreCalData;

static void fillPreCalData(PreCalData *data, float px, float py, QuadCurve* curve) {
	data->curve = curve;
	data->px = px;
	data->py = py;
    float x0px = curve->x0 - px;
    float y0py = curve->y0 - py;
    float x2px = curve->x2 - px;
    float y2py = curve->y2 - py;
	    
	data->f0 = x0px * x0px + y0py * y0py;
	data->f1 = x2px * x2px + y2py * y2py;
    data->df0 = curve->x10 * x0px + curve->y10 * y0py;
    data->df1 = curve->x21 * x2px + curve->y21 * y2py;
    data->axcxaycy2bx2by2 = curve->ax * x0px + curve->ay * y0py + 2.f * curve->bx2by2;
    data->bxcxbycy = data->df0;
    data->cx2cy2 = data->f0;
    data->cx = x0px;
    data->cy = y0py;
}

static float getRadius(float t, QuadCurve *curve) {
	float t2 = t * t;
	return t2 * curve->ar + t * curve->br + curve->cr;
}

static float4 getColor(float t, QuadCurve *curve) {
	float t2 = t * t;
	return t2 * curve->ac + t * curve->bc + curve->cc;
}

static float f(float t, PreCalData* data) {
	float t2 = t * t;
	float xtpx = t2 * data->curve->ax + t * 2.f * data->curve->bx + data->cx;
	float ytpy = t2 * data->curve->ay + t * 2.f * data->curve->by + data->cy;
	return xtpx * xtpx + ytpy * ytpy;
}

#if 1 // expanded version. maybe faster, but inconsistent with f().
static float df(float t, PreCalData* data) {
    return t * (t * (t *
           data->curve->ax2ay2 +
           3.f * data->curve->axbxayby) +
           data->axcxaycy2bx2by2) +
           data->bxcxbycy;
}

static float ddf(float t, PreCalData* data) {
    return t * (t *
           3.f * data->curve->ax2ay2 +
           6.f * data->curve->axbxayby) +
           data->axcxaycy2bx2by2;
}

static float dfRootByNewton(float t, PreCalData* data) {
    float t0;
    do {
        t0 = t;
        t = (t * t * (t * 2.f * data->curve->ax2ay2 + 3.f * data->curve->axbxayby) - data->bxcxbycy) /
            (t * (t * 3.f * data->curve->ax2ay2 + 6.f * data->curve->axbxayby) + data->axcxaycy2bx2by2);
    	DBG2("t t0", t0, t);
    } while (fabs(t - t0) > EPSILON);
    return t;
}
#else
static float df(float t, PreCalData* data) {
	float t2 = t * t;
	float xtpx = t2 * data->curve->ax + t * 2.f * data->curve->bx + data->cx;
	float ytpy = t2 * data->curve->ay + t * 2.f * data->curve->by + data->cy;
	float dxt = data->curve->ax * t + data->curve->bx;
	float dyt = data->curve->ay * t + data->curve->by;
	return xtpx * dxt + ytpy * dyt;
}

static float ddf(float t, PreCalData* data) {
	float t2 = t * t;
	float xtpx = t2 * data->curve->ax + t * 2.f * data->curve->bx + data->cx;
	float ytpy = t2 * data->curve->ay + t * 2.f * data->curve->by + data->cy;
	float dx = data->curve->ax * t + data->curve->bx;
	float dy = data->curve->ay * t + data->curve->by;
	return dx * dx + xtpx * data->curve->ax + dy * dy + ytpy * data->curve->ay;
}

static float dfRootByNewton(float t, PreCalData* data) {
    float t0;
    DBG3("axbxcx", data->curve->ax, data->curve->bx, data->cx);
    DBG3("aybycy", data->curve->ay, data->curve->by, data->cy);
    int i = 0;
    do {
        t0 = t;
        
        float t2 = t * t;
        float xtpx = t2 * data->curve->ax + t * 2.f * data->curve->bx + data->cx;
        float ytpy = t2 * data->curve->ay + t * 2.f * data->curve->by + data->cy;
        float dxt = data->curve->ax * t + data->curve->bx;
        float dyt = data->curve->ay * t + data->curve->by;
        float dft = xtpx * dxt + ytpy * dyt;
        float ddft = 2.f * (dxt * dxt + dyt * dyt) + xtpx * data->curve->ax + ytpy * data->curve->ay;
        t = t - dft / ddft;
        i++;
    DBG4("newton pxpy", t0, t, dft, ddft);
    } while (fabs(t - t0) > EPSILON);
    DBG("newton done", i);
    return t;
}

#endif

#define INVALID 1000.f

static float3 findCubicRoots(float a, float b, float c, float d) {
	float3 xxx;
    float p = (3.f * a * c - b * b) / (3.f * a * a);
    float q = (2.f * b * b * b - 9.f * a * b * c + 27.f * a * a * d) / (27.f * a * a * a);
    float pp = p * p * p / 27.f;
    float qq = q * q / 4.f;
    float det = pp + qq;

    if (p == 0.f) {
        if (q == 0.f) {
            xxx.x = 0.f;
            DBG("root type1", xxx);
        } else {
            xxx.x = cbrt(-q);
            DBG("root type2", xxx);
        }
        xxx.y = INVALID;
        xxx.z = INVALID;
    } else {
        if (q == 0.f) {
            xxx.x = 0.f;
            float sqrt_p = sqrt(-p);
            xxx.y = sqrt_p;
            xxx.z = -sqrt_p;
            DBG("root type3", xxx);
        } else if (det == 0.f) {
            xxx.x = 3.f * q / p;
            xxx.y = -3.f * q / (2.f * p);
            xxx.z = INVALID;
            DBG("root type4", xxx);
        } else if (det < 0.f) {
            float acosarg = (3.f * q) / (2.f * p) * sqrt(-3.f / p);
            float theta = acos(acosarg) / 3.f;
            float p32 = 2.f * sqrt(-p / 3.f);
            xxx.x = p32 * cos(theta);
            xxx.y = p32 * cos(theta + PI_2_3);
            xxx.z = p32 * cos(theta - PI_2_3);
            DBG("root type5", xxx);
        } else {
            float detSqrt = sqrt(det);
            float u = cbrt(-q / 2.f + detSqrt);
            float v = cbrt(-q / 2.f - detSqrt);
            xxx.x = u + v;
            xxx.y = INVALID;
            xxx.z = INVALID;
            DBG("root type6", xxx);
        }
    }
    float offset = b / (3.f * a);
    xxx.x -= offset;
    if (xxx.y != INVALID) {
    	xxx.y -= offset;
    }
    if (xxx.z != INVALID) {
    	xxx.z -= offset;
    }
	DBG("xxx", xxx);
	return xxx;
}

static float findCubicDiscriminant(float a, float b, float c, float d) {
	float aa = a;
	a = b / aa;
	b = c / aa;
	c = d / aa;
	//rsDebug("a b c d", a, b, c);
	float q = (3.f * b - a * a) / 3.f;
	float r = (2.f * a * a * a - 9.f * a * b + 27.f * c) / 27.f;
    float3 xxx;
    float qq = q * q * q / 27.f;
    float rr = r * r / 4.f;
	float m = rr + qq;
	return m;
}

static float ddfDet(PreCalData* data) {
    return 9.f * data->curve->axbxayby * data->curve->axbxayby - 3.f * data->curve->ax2ay2 * data->axcxaycy2bx2by2;
}


static void findDistanceNewton(float x, float y, PreCalData *data, float3* res) {
    float min;
    float minDistance;

    float det = ddfDet(data);
    if (det > 0.f) {
        float detSqrt_a2 = sqrt(det) / (3.f * data->curve->ax2ay2);
        float a2 = data->curve->tc - detSqrt_a2;
        float b2 = data->curve->tc + detSqrt_a2;

        // Divide range by ddf roots a2, b2.
        // So newton's method converge easily in the range.
        
        // first minimum
        min = -1.f;
        if (T0 < a2) {
        	if (data->df0 < 0.f) {
        		if (T1 <= a2) {
        			if (data->df1 >= 0.f) {
                        min = dfRootByNewton(T0, data);
                        minDistance = f(min, data);
                        DBG2("newton11", min, minDistance);
        			}
        		} else {
        			if (df(a2, data) >= 0.f) {
                        min = dfRootByNewton(T0, data);
                        minDistance = f(min, data);
                        DBG2("newton12", min, minDistance);
        			}
        		}
        	} else if (data->df0 == 0.f) {
        		min = T0;
                minDistance = data->f0;
                DBG2("newton13", min, minDistance);
        	}
        }

        if (min >= T0 && min <= T1 && minDistance <= res->y) {
        	res->x = min;
			res->y = minDistance;
			res->z = IN_RANGE;
            DBG("newton14", res);
        }

        // second minimum
        min = -1.f;
        if (b2 < T1) {
        	if (data->df1 > 0.f) {
        		if (b2 <= T0) {
        			if (data->df0 <= 0.f) {
                        min = dfRootByNewton(T1, data);
                        minDistance = f(min, data);
                        DBG2("newton3", min, minDistance);
        			}
        		} else {
        			if (df(b2, data) <= 0.f) {
                        min = dfRootByNewton(T1, data);
                        minDistance = f(min, data);
                        DBG2("newton4", min, minDistance);
        			}
        		}
        	} else if (data->df1 == 0.f) {
        		min = T1;
                minDistance = data->f1;
                DBG2("newton23", min, minDistance);
        	}
        }

        if (min >= T0 && min <= T1 && minDistance <= res->y) {
        	res->x = min;
			res->y = minDistance;
			res->z = IN_RANGE;
            DBG("newton24", res);
        }
    } else {
        if (data->df0 <= 0.f && data->df1 >= 0.f) {
            float dfc = df(data->curve->tc, data);
            min = dfRootByNewton(dfc > 0.f ? T0 : T1, data);
            minDistance = f(min, data);
            if (min >= T0 && min <= T1 && minDistance <= res->y) {
                res->x = min;
                res->y = minDistance;
                res->z = IN_RANGE;
                DBG("newton31", res);
            }
        }
    }
}

static void setIfValid(float x, PreCalData *data, float3 *res) {
	if (x >= T0 && x <= T1) {
		float ft = f(x, data);	
		if (ft <= res->y) {
			res->x = x;
			res->y = ft;
			res->z = IN_RANGE;
		}
	}
}

static void findDistanceCardano(float x, float y, PreCalData *data, float3 *res) {
	float a = data->curve->ax2ay2;
	float b = 3.f * data->curve->axbxayby;
	float c = data->axcxaycy2bx2by2;
	float d = data->bxcxbycy;
	float3 roots = findCubicRoots(a, b, c, d);
	
	setIfValid(roots.x, data, res);
	setIfValid(roots.y, data, res);
	setIfValid(roots.z, data, res);
}

// https://git.gnome.org/browse/gimp/tree/app/operations/gimpoperationnormalmode.c
static float4 blendColor(float4 src, float3 rgb, float a) {
	float4 dst;
	DBG("srcRgba1", src);
	float inAlpha = src.a;
	float auxAlpha = a;
	float outAlpha = auxAlpha + inAlpha - auxAlpha * inAlpha; // gimp normal
	DBG3("src3", auxAlpha, inAlpha, outAlpha);
	if (outAlpha > 0.f) {
		dst.rgb = rgb * auxAlpha + src.rgb * (1.f - auxAlpha);
        DBG("dst.rgb", dst.rgb);
	} else {
		dst.rgb = src.rgb;
	}
	dst.a = outAlpha;
    DBG("dstRgba", dst);
	return dst;
}

static float4 blendColorNormal(float4 src, float3 rgb, float a) {
	float4 dst;
	DBG("srcRgba1", src);
	if (src.a > 0.f) src.rgb /= src.a; // undo alpha-premultiplication
	DBG("srcRgba2", src);
	float inAlpha = src.a;
	float auxAlpha = a;
	float outAlpha = auxAlpha + inAlpha - auxAlpha * inAlpha;
	float diff = fabs(inAlpha - outAlpha);
	float maxAlpha = fmax(inAlpha, outAlpha);
	outAlpha = outAlpha * diff + maxAlpha * (1.f - diff);
	DBG3("src3", auxAlpha, inAlpha, outAlpha);
	if (outAlpha > 0.f) {
		float weight = inAlpha * (1.f - auxAlpha);
		dst.rgb = (rgb * auxAlpha + src.rgb * weight) / outAlpha;
        DBG("dst.rgb", dst.rgb);
	} else {
		dst.rgb = src.rgb;
	}
	//dst.rgb = rgb;
	dst.rgb *= outAlpha; // redo alpha-premultiplication
	dst.a = outAlpha;
    DBG("dstRgba", dst);
	return dst;
}

static float4 blendColorMax(float4 src, float3 rgb, float a) {
	float4 dst;
	DBG("srcRgba1", src);
	if (src.a > 0.f) src.rgb /= src.a; // undo alpha-premultiplication
	DBG("srcRgba2", src);
	float inAlpha = src.a;
	float auxAlpha = a;
	float outAlpha = fmax(inAlpha, auxAlpha);
	DBG3("src3", auxAlpha, inAlpha, outAlpha);
	if (outAlpha > 0.f) {
		float weight = inAlpha * (1.f - auxAlpha);
		dst.rgb = (rgb * auxAlpha + src.rgb * weight) / outAlpha;
        DBG("dst.rgb", dst.rgb);
	} else {
		dst.rgb = src.rgb;
	}
	dst.rgb = rgb;
	dst.rgb *= outAlpha; // redo alpha-premultiplication
	dst.a = outAlpha;
    DBG("dstRgba", dst);
	return dst;
}

static float4 blendColorAddition(float4 src, float3 rgb, float a) {
	if (src.a > 0.f) src.rgb /= src.a; // undo alpha-premultiplication
	float in = src.a;
	float comp_alpha = fmin(in, a);
	float new_alpha = in + (1.f - in) * comp_alpha;
	float4 dst;
	if (comp_alpha > 0.f && new_alpha > 0.f) {
		float ratio = comp_alpha / new_alpha;
		dst.rgb = rgb * ratio + src.rgb * (1.f - ratio);
	} else {
		dst.rgb = src.rgb;
	}
	dst.a = a;
	dst.rgb *= a; // redo alpha-premultiplication
	return dst;
}

// similar to normal distribution using quadratic
static float getAlphaFromDistance(float d, float r) {
	if (d >= r) {
		return 0.f;
#if 0
	} else {
		return 1.f - d / r;
	}
#else
	} else if (d <= r - blurRadius) {
		return 1.f;
	} else if (d > r - blurRadius / 2.f) {
		float x = (d - r) / blurRadius;
		return 2.f * x * x;
	} else {
		float x = (d - (r - blurRadius)) / blurRadius;
		return 1.f - 2.f * x * x;
	}
#endif
}

static float3 getDistanceFromCurve(QuadCurve *curve, uint32_t x, uint32_t y) {
	float3 ret = {0.f, 0.f, IN_RANGE};
    float min;
    float minDistance;
    float edge;
    if (curve->type == TYPE_LINE) {
    	if (curve->ax2ay2 == 0.f) { // start and end is same.(TODO:what if control point is different?)
    		return ret;
    	}
    	float x0p = curve->x0 - x;
    	float y0p = curve->y0 - y;
    	float xm;
    	float ym;
    	min = -(curve->ax*x0p + curve->ay*y0p) / curve->ax2ay2;
    	if (min < T0) {
    		min = T0;
    		xm = x0p;
    		ym = y0p;
    		edge = BEFORE_RANGE;
    	} else if (min > T1) {
    		min = T1;
    		xm = curve->x2 - x;
    		ym = curve->y2 - y;
    		edge = AFTER_RANGE;
    	} else {
            xm = curve->x0 + min * curve->ax - x;
            ym = curve->y0 + min * curve->ay - y;
    		edge = IN_RANGE;
    	}
        minDistance = xm * xm + ym * ym;
    } else {
        PreCalData data;
        fillPreCalData(&data, (float)x, (float)y, curve);
		float3 res; // {minT, minDistance, edge}
        if (data.f0 < data.f1) {
        	res.x = T0;
        	res.y = data.f0;
        	res.z = BEFORE_RANGE;
        } else { // if f0==f1, choose f1. because 1 is later.
        	res.x = T1;
        	res.y = data.f1;
        	res.z = AFTER_RANGE;
        }
    #if USE_NEWTON == 1
        findDistanceNewton((float)x, (float)y, &data, &res);
    #else
        findDistanceCardano((float)x, (float)y, &data, &res);
    #endif

        min = res.x;
        minDistance = res.y;
        edge = res.z;
    }
    
    ret.x = min;
    ret.y = minDistance;
    ret.z = edge;
    
    return ret;
}

void root(const uchar4 *v_in, uchar4 *v_out, const void* usrData, uint32_t x, uint32_t y) {
    QuadCurve* curve = (QuadCurve*)usrData;
 	float3 curDistance = getDistanceFromCurve(curve, x, y);
    float min = curDistance.x;
    float minDistance = curDistance.y;
    float edge = curDistance.z;
    
    // TODO
    // if curve end is rectangle, it looks 
    //if (edge != IN_RANGE) return; // good for normal. normal highlights overlap area.
    //if (!curve->useFirstCap && edge == BEFORE_RANGE) return; // good for blend max
    
    float radius = getRadius(min, curve);

    float alpha = getAlphaFromDistance(sqrt(minDistance), radius);
    if (alpha > 0.f) {
    	float4 srcRgba = rsUnpackColor8888(*v_out);
    	float4 color = getColor(min, curve);
    	float3 dstRgb = color.rgb;
    	// TODO
    	// blend normal makes overlap too dark. and if draw slowly saw-shape occurs. good for cross-overlap.
    	// blend max cuts inside-knee deeply.
    	// what shall I do?
    	// IDEA1 : blend max for just-previous curve. normal for rest. maybe this will solve saw-shape for neighbor curve and cross-overlap for far curve.
    	// -> FAIL. if slow
    	float4 dstRgba = blendColorMax(srcRgba, dstRgb, alpha);
    	*v_out = rsPackColorTo8888(dstRgba.r, dstRgba.g, dstRgba.b, dstRgba.a);
    }
}

void moveTo(float x, float y, float r, int c) {
	float3Set(&lastPoint, x, y, r);
	float4Set(&lastColor, c);
	
	prevCurve = NULL;
	curCurve = &curve1;
}

void quadTo(float x1, float y1, float r1, int c1, float x2, float y2, float r2, int c2) {
	float x0 = lastPoint.x;
	float y0 = lastPoint.y;
	float r0 = lastPoint.z;
	float4 color0;
	float4Copy(&color0, &lastColor);
	float4 color1;
	float4Set(&color1, c1);
	float4 color2;
	float4Set(&color2, c2);
	fillQuadCurve(curCurve, x0, y0, r0, color0, x1, y1, r1, color1, x2, y2, r2, color2, false);
	float3Set(&lastPoint, x2, r2, r2);
	float4Copy(&lastColor, &color2);
	
	rs_script_call_t sc;

	float maxr = fmax(fmax(r0, r1), r2);
	sc.xStart = fmin(fmin(x0, x1), x2) - maxr;
	sc.xEnd = fmax(fmax(x0, x1), x2) + maxr;
	sc.yStart = fmin(fmin(y0, y1), y2) - maxr;
	sc.yEnd = fmax(fmax(y0, y1), y2) + maxr;
	sc.zStart = 0;
	sc.zEnd = 0;
	rsForEach(script, allocation, allocation, curCurve, sizeof(QuadCurve), &sc);
}