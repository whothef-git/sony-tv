package tv.widgetv.home;

import java.util.List;

/** No transport assumptions: a future real repository can implement this contract. */
interface SmartHomeRepository {
    default boolean isDemo(){return true;}
    default void setActive(boolean active){}
    default void shutdown(){}
    default String statusText(){return "DEMO MODE · Simulated devices & readings";}
    long OFFLINE_GRACE_MS = 45_000;
    List<SmartDevice> all();
    List<SmartDevice> visible();
    SmartDevice find(String id);
    void addListener(Runnable listener);
    void removeListener(Runnable listener);
    long nextVisibilityDelay();
    void togglePower(String id);
    void adjustTemperature(String id, int delta);
    void setMode(String id, String mode);
    void setFanSpeed(String id, String speed);
    void toggleOutlet(String id, int outlet);
}
