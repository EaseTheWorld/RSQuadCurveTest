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