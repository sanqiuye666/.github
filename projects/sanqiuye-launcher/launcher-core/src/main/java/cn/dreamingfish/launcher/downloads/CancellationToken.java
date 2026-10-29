package cn.dreamingfish.launcher.downloads;
import java.io.InterruptedIOException;
import java.util.*;
public final class CancellationToken {
 private boolean cancelled;
 private final Set<Runnable> callbacks=new LinkedHashSet<>();
 public void cancel(){List<Runnable> pending;synchronized(this){if(cancelled)return;cancelled=true;pending=List.copyOf(callbacks);callbacks.clear();}for(var callback:pending)try{callback.run();}catch(RuntimeException ignored){/* Cancellation must notify every subscriber. */}}
 public synchronized boolean isCancelled(){return cancelled;}
 public void check() throws InterruptedIOException {if(isCancelled()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("操作已取消");}
 public Registration onCancel(Runnable action){boolean now;synchronized(this){now=cancelled;if(!now)callbacks.add(action);}if(now)action.run();return ()->{synchronized(this){callbacks.remove(action);}};}
 @FunctionalInterface public interface Registration extends AutoCloseable {@Override void close();}
}
