/*
 * Ponte JNI entre o app (Java) e o codificador MP3 LAME (LGPL).
 * Recebe áudio PCM 16 bits e devolve quadros MP3.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "lame.h"

static void set_tag(JNIEnv *env, lame_t l, jstring value, int is_title) {
    if (value == NULL) {
        return;
    }
    const char *s = (*env)->GetStringUTFChars(env, value, NULL);
    if (s != NULL) {
        if (is_title) {
            id3tag_set_title(l, s);
        } else {
            id3tag_set_artist(l, s);
        }
        (*env)->ReleaseStringUTFChars(env, value, s);
    }
}

JNIEXPORT jlong JNICALL
Java_com_kamarguinho_supertube_download_Mp3Encoder_nativeInit(JNIEnv *env, jclass cls,
        jint sample_rate, jint channels, jint bitrate, jstring title, jstring artist) {
    lame_t l = lame_init();
    if (l == NULL) {
        return 0;
    }
    lame_set_in_samplerate(l, sample_rate);
    lame_set_num_channels(l, channels);
    lame_set_out_samplerate(l, sample_rate);
    lame_set_brate(l, bitrate);
    lame_set_mode(l, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_quality(l, 3);
    id3tag_init(l);
    id3tag_add_v2(l);
    id3tag_v2_only(l);
    set_tag(env, l, title, 1);
    set_tag(env, l, artist, 0);
    if (lame_init_params(l) < 0) {
        lame_close(l);
        return 0;
    }
    return (jlong) (intptr_t) l;
}

JNIEXPORT jint JNICALL
Java_com_kamarguinho_supertube_download_Mp3Encoder_nativeEncode(JNIEnv *env, jclass cls,
        jlong handle, jshortArray pcm, jint samples_per_channel, jint channels,
        jbyteArray out) {
    lame_t l = (lame_t) (intptr_t) handle;
    jshort *in = (*env)->GetShortArrayElements(env, pcm, NULL);
    jbyte *buf = (*env)->GetByteArrayElements(env, out, NULL);
    const jint out_size = (*env)->GetArrayLength(env, out);
    int n;
    if (channels == 1) {
        n = lame_encode_buffer(l, in, in, samples_per_channel, (unsigned char *) buf, out_size);
    } else {
        n = lame_encode_buffer_interleaved(l, in, samples_per_channel,
                (unsigned char *) buf, out_size);
    }
    (*env)->ReleaseShortArrayElements(env, pcm, in, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, out, buf, 0);
    return n;
}

JNIEXPORT jint JNICALL
Java_com_kamarguinho_supertube_download_Mp3Encoder_nativeFlush(JNIEnv *env, jclass cls,
        jlong handle, jbyteArray out) {
    lame_t l = (lame_t) (intptr_t) handle;
    jbyte *buf = (*env)->GetByteArrayElements(env, out, NULL);
    const jint out_size = (*env)->GetArrayLength(env, out);
    const int n = lame_encode_flush(l, (unsigned char *) buf, out_size);
    (*env)->ReleaseByteArrayElements(env, out, buf, 0);
    return n;
}

JNIEXPORT void JNICALL
Java_com_kamarguinho_supertube_download_Mp3Encoder_nativeClose(JNIEnv *env, jclass cls,
        jlong handle) {
    lame_t l = (lame_t) (intptr_t) handle;
    if (l != NULL) {
        lame_close(l);
    }
}
