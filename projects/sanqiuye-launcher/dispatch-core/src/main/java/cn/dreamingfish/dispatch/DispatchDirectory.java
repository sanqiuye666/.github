package cn.dreamingfish.dispatch;

import java.util.List;

/** Future authenticated directory adapter. M1 has no database or remote implementation. */
public interface DispatchDirectory {
    List<PlayerGroup> groups();
    List<PanelIdentity> players();
}
