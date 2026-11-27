LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := libtermux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
LOCAL_C_INCLUDES := $(TERMUX_BOOTSTRAP_DIR)
include $(BUILD_SHARED_LIBRARY)
