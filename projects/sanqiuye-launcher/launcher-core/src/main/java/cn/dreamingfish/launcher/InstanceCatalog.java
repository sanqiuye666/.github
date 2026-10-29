package cn.dreamingfish.launcher;

import java.util.List;

/** Local instance descriptions; installation and launch readiness are separate capabilities. */
public interface InstanceCatalog {
    List<GameInstance> instances();
}
