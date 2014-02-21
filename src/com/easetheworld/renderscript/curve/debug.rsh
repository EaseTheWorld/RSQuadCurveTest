#pragma version(1)
#pragma rs java_package_name(com.easetheworld.renderscript.curve)

#define DEBUG 0
#if DEBUG == 1
#define DBG(tag, v0) rsDebug(tag, v0)
#define DBG2(tag, v0, v1) rsDebug(tag, v0, v1)
#define DBG3(tag, v0, v1, v2) rsDebug(tag, v0, v1, v2)
#define DBG4(tag, v0, v1, v2, v3) rsDebug(tag, v0, v1, v2, v3)
#else
#define DBG(tag, v0)
#define DBG2(tag, v0, v1)
#define DBG3(tag, v0, v1, v2)
#define DBG4(tag, v0, v1, v2, v3)
#endif