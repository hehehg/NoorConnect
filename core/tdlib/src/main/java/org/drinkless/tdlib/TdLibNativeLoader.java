package org.drinkless.tdlib;

final class TdLibNativeLoader {
    private static boolean loaded;

    private TdLibNativeLoader() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }
        try {
            System.loadLibrary("tdjni");
            loaded = true;
        } catch (UnsatisfiedLinkError error) {
            throw new UnsatisfiedLinkError(
                "Unable to load TDLib native library 'tdjni'. Check java.library.path and packaged native dependencies: "
                    + error.getMessage()
            );
        }
    }
}