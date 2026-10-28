package tv.widgetv.home;

/** Injectable device boundary: production uses LAN transport; automated tests use synthetic peers. */
interface SmartDeviceDriver {
    SmartDevice read(SmartDevice device)throws Exception;
    SmartDevice setPower(SmartDevice device,boolean on)throws Exception;
    SmartDevice setOutlet(SmartDevice device,int index,boolean on)throws Exception;
}
