# Keep class and method names in support diagnostics.
-dontobfuscate
# Native socket code constructs this result and populates peer fields by name.
-keep class com.termux.shared.jni.models.JniResult {
    public <init>(int,int,java.lang.String,int);
}
-keepclassmembers class com.termux.shared.net.socket.local.PeerCred {
    public int pid;
    public int uid;
    public int gid;
    public java.lang.String pname;
    public java.lang.String cmdline;
}
