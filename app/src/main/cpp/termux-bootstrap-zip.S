#include "termux-bootstrap-archive.inc"

     .global blob
     .global blob_size
     .section .rodata
 blob:
     .incbin TERMUX_BOOTSTRAP_ARCHIVE
 1:
 blob_size:
     .int 1b - blob
