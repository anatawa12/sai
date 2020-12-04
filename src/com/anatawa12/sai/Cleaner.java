package com.anatawa12.sai;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

class Cleaner {
    private static ReferenceQueue<Object> queue = new ReferenceQueue<>();

    public abstract static class PhantomAction<T> extends PhantomReference<T> implements Runnable {
        public PhantomAction(T referent) {
            super(referent, queue);
        }
    }

    static {
        Thread thread = new Thread("sai stack edit rule cleaner") {
            @SuppressWarnings("InfiniteLoopStatement")
            @Override
            public void run() {
                try {
                    while (true) {
                        Runnable action = (Runnable) queue.remove();
                        try {
                            action.run();
                        } catch (Error | RuntimeException ignored) {
                        }
                    }
                } catch (InterruptedException ignored) {
                }
            }
        };

        thread.setDaemon(true);
        thread.start();
    }
}
