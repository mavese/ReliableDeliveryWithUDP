public class Driver
{
    public static void main(String[] args)
    {
        //this will run sender and receiver

        String fname = "/home/mavese/IdeaProjects/ReliableDeliveryWithUDP/src/test";
        RSendUDP sender = new RSendUDP();
        sender.setMode(1);
        sender.setFilename(fname);
        RReceiveUDP rcvr = new RReceiveUDP();
        new Thread()
        {
            public void run()
            {
                rcvr.receiveFile();
            }
        }.start();
        sender.sendFile();

//        byte [] buff = ("Hello world").getBytes();
//        byte [] packet = sender.packPacket(buff, 3, 0);
//        for (byte b:packet)
//        {
//            System.out.println(b);
//        }
//        System.out.println("\n");
//        RReceiveUDP.Packet unpacked = rcvr.unpackPacket(packet);
//        System.out.println(unpacked.flag);
//        for (byte b:unpacked.seqNum)
//        {
//            System.out.println(b);
//        }
//        for (byte b:unpacked.data)
//        {
//            System.out.println(b);
//        }
    }
}
