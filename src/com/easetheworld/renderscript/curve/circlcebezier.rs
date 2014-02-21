#pragma version(1)
#pragma rs java_package_name(com.easetheworld.renderscript.curve)

#include "debug.rsh"

static void float3Copy(float3 *dst, float3 *src) {
	dst->x = src->x;
	dst->y = src->y;
	dst->z = src->z;
}

static void float3Set(float3 *f, float x, float y, float z) {
	f->x = x;
	f->y = y;
	f->z = z;
}

static float4 getColor8888(int color) {
	uchar a = (color & 0xff000000) >> 24;
	uchar r = (color & 0x00ff0000) >> 16;
	uchar g = (color & 0x0000ff00) >> 8;
	uchar b = (color & 0x000000ff);
	uchar4 cc = {r, g, b, a};
	return rsUnpackColor8888(cc);
}

static void float4Copy(float4 *dst, float4 *src) {
	dst->r = src->r;
	dst->g = src->g;
	dst->b = src->b;
	dst->a = src->a;
}

static void float4Set(float4 *f, int c) {
	float4 rgba = getColor8888(c);
	float4Copy(f, &rgba);
}

// bezier curve 0 ~ 1
#define T0 0.f
#define T1 1.f
#define INVALID -1.f

typedef struct {
	float3 point;
	float4 color;
} Circle;

typedef float (*GetDistanceAtT)(float t);
typedef void (*GetCircleAtDistance)(float distance, Circle *circle);

typedef struct {
	// given data
	float3 p0; // x0, y0, radius0
	float3 p1; // x1, y1, radius1
	float3 p2; // x2, y2, radius2
	float4 c0;
	float4 c1;
	float4 c2;

	// precalculated data
    float3 ap;
    float3 bp;
    float3 cp;

    float4 ac;
    float4 bc;
    float4 cc;

    float a;
    float b;
    float c;
    float sqrta2;
    float sqrta8;
    float sqrtc;
    float b24ac;
    float integral0;
    float length;
	
    GetDistanceAtT getDistanceAtT;
    GetCircleAtDistance getCircleAtDistance;
} BezierCurve;

static BezierCurve curve;

static float3 lastPoint;
static float4 lastColor;
static float lastCurveEndDistance;
static float totalDistance;
const static float RADIUS_RATIO = 0.25f;

float blurRadius = 10.f;

rs_script script;
rs_allocation allocation;

static float lineGetDistanceAtT(float t) {
	return curve.length * t;	
}

static void lineGetCircleAtDistance(float distance, Circle *circle) {
    float t = distance / curve.length;
    circle->point = curve.p0 + curve.ap * t;
    circle->color = curve.c0 + curve.ac * t;
}

static float quadIntegral(float t) {
    float result;
    if (curve.a == 0.f) {
        if (curve.b == 0.f) {
            result = curve.sqrtc * t;
            DBG("quadIntegral1", result);
        } else {
            float btc = curve.b * t + curve.c;
            result = 2.f * btc * sqrt(btc) / (3.f * curve.b);
            DBG("quadIntegral2", result);
        }
    } else {
        float pt = 2.f * t * curve.a + curve.b;
        float qt = sqrt(t * (t * curve.a + curve.b) + curve.c);
        DBG3("quadIntegral pt qt", pt, qt, curve.sqrta2 * qt + pt);
        float logarg = curve.sqrta2 * qt + pt;
        DBG2("b24ac", curve.b24ac, logarg);
        if (curve.b24ac == 0.f || logarg <= 0.f) {
            result = (curve.sqrta2 * pt * qt) / curve.sqrta8;
            DBG("quadIntegral3", result);
        } else {
            result = (curve.sqrta2 * pt * qt - curve.b24ac * log(logarg)) / curve.sqrta8;
            DBG("quadIntegral4", result);
        }
    }
    return result;
}

static float quadGetDistanceAtT(float t) {
	return quadIntegral(t) - curve.integral0;
}

#define BINARY_SEARCH_EPSILON 0.1f;

static void quadGetCircleAtDistance(float distance, Circle *circle) {
	float a = T0;
	float b = T1;
	float m;
	float t = -1.f;
	if (distance == 0.f) {
		t = 0.f;
	} else {
	int i = 0;
        float d = distance + curve.integral0;
        float da = d - BINARY_SEARCH_EPSILON;
        float db = d + BINARY_SEARCH_EPSILON;
	DBG3("quadGetPoint2", d, da, db);
        float fm;
        while(true) {
            m = (a + b) * 0.5f;
            fm = quadIntegral(m);
            if (fm < da) {
            DBG4("binary1", a, m, b, fm);
                a = m;
            } else if (fm > db) {
            DBG4("binary2", a, m, b, fm);
                b = m;
            } else {
            DBG4("binary3", a, m, b, fm);
                t = m;
                break;
            }
        }
	}
	
	if (t == -1.f) {
		return;
	}
    float t2 = t * t;
    circle->point = t2 * curve.ap + t * curve.bp + curve.cp;
    circle->color = t2 * curve.ac + t * curve.bc + curve.cc;
}

static void drawCircle(Circle *circle) {
    DBG("drawCircle point", circle->point);
    DBG("drawCircle color", circle->color);
	float r = circle->point.z;
	rs_script_call_t sc;
	sc.xStart = circle->point.x - r;
	sc.xEnd = circle->point.x + r;
	sc.yStart = circle->point.y - r;
	sc.yEnd = circle->point.y + r;
	sc.zStart = 0;
	sc.zEnd = 0;
	rsForEach(script, allocation, allocation, circle, sizeof(Circle), &sc);
}

static void drawCircles() {
    Circle circle;
    while(true) {
    	float nextDistance = totalDistance - lastCurveEndDistance;
    	if (nextDistance > curve.length) {
    		break;
    	}
    	curve.getCircleAtDistance(nextDistance, &circle);
    	drawCircle(&circle);
    	totalDistance += circle.point.z * RADIUS_RATIO;
    	DBG2("totalDistance", totalDistance, lastCurveEndDistance);
    }
    lastCurveEndDistance += curve.length;
}

void moveTo(float x, float y, float r, int c) {
	float3Set(&lastPoint, x, y, r);
	float4Set(&lastColor, c);

	lastCurveEndDistance = 0.f;
	totalDistance = 0.f;
}

void lineTo(float x, float y, float r, int c) {
	float3Copy(&curve.p0, &lastPoint);
	float4Copy(&curve.c0, &lastColor);

	float3Set(&curve.p1, x, y, r);
	float4Set(&curve.c1, c);

	float3Copy(&lastPoint, &curve.p1);
	float4Copy(&lastColor, &curve.c1);

    curve.getDistanceAtT = &lineGetDistanceAtT;
    curve.getCircleAtDistance = &lineGetCircleAtDistance;
	curve.ap = curve.p1 - curve.p0;
	curve.ac = curve.c1 - curve.c0;
	curve.length = sqrt(curve.ap.x * curve.ap.x + curve.ap.y * curve.ap.y);
    
    drawCircles();
}

void quadTo(float x1, float y1, float r1, int c1, float x2, float y2, float r2, int c2) {
	float3Copy(&curve.p0, &lastPoint);
	float4Copy(&curve.c0, &lastColor);

	float3Set(&curve.p1, x1, y1, r1);
	float4Set(&curve.c1, c1);

	float3Set(&curve.p2, x2, y2, r2);
	float4Set(&curve.c2, c2);

	float3Copy(&lastPoint, &curve.p2);
	float4Copy(&lastColor, &curve.c2);

	DBG("quadToP0", curve.p0);
	DBG("quadToP1", curve.p1);
	DBG("quadToP2", curve.p2);
	DBG("quadToC0", curve.c0);
	DBG("quadToC1", curve.c1);
	DBG("quadToC2", curve.c2);

    curve.getDistanceAtT = &quadGetDistanceAtT;
    curve.getCircleAtDistance = &quadGetCircleAtDistance;
    
    curve.ap = curve.p0 - 2.f * curve.p1 + curve.p2;
    curve.bp = 2.f * (curve.p1 - curve.p0);
    curve.cp = curve.p0;
    curve.ac = curve.c0 - 2.f * curve.c1 + curve.c2;
    curve.bc = 2.f * (curve.c1 - curve.c0);
    curve.cc = curve.c0;

	// ds = dx^2+dy^2
    curve.a = 4.f * (curve.ap.x * curve.ap.x + curve.ap.y * curve.ap.y);
    curve.b = 4.f * (curve.ap.x * curve.bp.x + curve.ap.y * curve.bp.y);
    curve.c = curve.bp.x * curve.bp.x + curve.bp.y * curve.bp.y;
    curve.sqrta2 = 2.f * sqrt(curve.a);
    curve.sqrta8 = 4.f * curve.a * curve.sqrta2;
    curve.sqrtc = sqrt(curve.c);
    curve.b24ac = curve.b * curve.b - 4.f * curve.a * curve.c;
    DBG("b24ac", curve.b24ac);
    DBG3("abc", curve.a, curve.b, curve.c);
    curve.integral0 = quadIntegral(T0);
    DBG("integral0", curve.integral0);

    curve.length = curve.getDistanceAtT(T1);
    DBG("quad distance", curve.length);
    
    drawCircles();
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

static float4 blendColorNormal(float4 src, float3 rgb, float a) {
	float4 dst;
	if (src.a > 0.f) src.rgb /= src.a; // undo alpha-premultiplication
	float inAlpha = src.a;
	float auxAlpha = a;
	float outAlpha = auxAlpha + inAlpha - auxAlpha * inAlpha;
	float diff = fabs(inAlpha - outAlpha);
	float maxAlpha = fmax(inAlpha, outAlpha);
	outAlpha = outAlpha * diff + maxAlpha * (1.f - diff);
	if (outAlpha > 0.f) {
		float weight = inAlpha * (1.f - auxAlpha);
		dst.rgb = (rgb * auxAlpha + src.rgb * weight) / outAlpha;
	} else {
		dst.rgb = src.rgb;
	}
	//dst.rgb = rgb;
	dst.rgb *= outAlpha; // redo alpha-premultiplication
	dst.a = outAlpha;
	return dst;
}

static float getAlphaFromDistance(float d, float r) {
	if (d >= r) {
		return 0.f;
	} else if (d <= r - blurRadius) {
		return 1.f;
	} else if (d > r - blurRadius / 2.f) {
		float x = (d - r) / blurRadius;
		return 2.f * x * x;
	} else {
		float x = (d - (r - blurRadius)) / blurRadius;
		return 1.f - 2.f * x * x;
	}
}

void root(const uchar4 *v_in, uchar4 *v_out, const void* usrData, uint32_t x, uint32_t y) {
	Circle *circle = (Circle*)usrData;
	float3 point = circle->point;
	float distance = hypot(x - point.x, y - point.y);
    float alpha = getAlphaFromDistance(distance, point.z);
    if (alpha > 0.f) {
    	float4 srcRgba = rsUnpackColor8888(*v_out);
    	float3 dstRgb = circle->color.rgb;
    	float4 dstRgba = blendColorNormal(srcRgba, dstRgb, alpha);
    	*v_out = rsPackColorTo8888(dstRgba.r, dstRgba.g, dstRgba.b, dstRgba.a);
    }
}