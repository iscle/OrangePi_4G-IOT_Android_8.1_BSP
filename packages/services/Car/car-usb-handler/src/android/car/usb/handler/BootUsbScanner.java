package android.car.usb.handler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

public class BootUsbScanner extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: move probing of devices to a service, since AoapInterface.isSupported() could take
        // up to 2 seconds and many USB devices could be connected.
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (AoapInterface.isDeviceInAoapMode(device)) {
                // This could happen if we reboot. We should try to handle this accessory.
                handle(context, device);
            } else {
                UsbDeviceConnection connection = UsbUtil.openConnection(manager, device);
                try {
                    if (AoapInterface.isSupported(connection)) {
                        handle(context, device);
                    }
                } finally {
                    connection.close();
                }
            }
        }
    }

    private void handle(Context context, UsbDevice device) {
        Intent manageDevice = new Intent(context, UsbHostManagementActivity.class);
        manageDevice.setAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        manageDevice.putExtra(UsbManager.EXTRA_DEVICE, device);
        manageDevice.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(manageDevice);
    }
}
