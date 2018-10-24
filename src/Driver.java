import java.net.InetSocketAddress;

public class Driver
{
    public static void main(String[] args)
    {
        //this will run sender and receiver

        String fname = "/home/mavese/IdeaProjects/ReliableDeliveryWithUDP/src/test";
        RSendUDP sender = new RSendUDP();
        sender.setMode(1);
        sender.setFilename(fname);
        sender.setModeParameter(100000);
        sender.setTimeout(50);
        sender.setReceiver(new InetSocketAddress("localhost", 2024));
        RReceiveUDP rcvr = new RReceiveUDP();
        rcvr.setLocalPort(2024);
        rcvr.setFilename("/home/mavese/IdeaProjects/ReliableDeliveryWithUDP/src/gi1.gif");
        new Thread()
        {
            public void run()
            {
                rcvr.receiveFile();
                Thread.currentThread().stop();
            }
        }.start();
        sender.sendFile();
    }
}
