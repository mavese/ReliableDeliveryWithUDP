import java.net.DatagramPacket;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import edu.utulsa.unet.RSendUDPI;
import edu.utulsa.unet.UDPSocket;


public class RSendUDP implements RSendUDPI
{

    /***
     * This method currently functions correctly it has been tested. When pulling sequence number out
     * must make sure to put it in an unsigned integer as the leading bit could be one
     * @param data the data to pack in packet
     * @param number the sequence number of this packet
     * @return byte array that is the packet to send.
     */
    private byte [] packPacket(byte [] data, int number, int flag)
    {
        byte [] tseq = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(number).array();
        byte [] seq = new byte[SEQSIZE];
        for (int i = tseq.length - (tseq.length - SEQSIZE), j = 0; i < tseq.length; i++, j++)
        {
            seq[j] = tseq[i];
        }
        byte [] packet = new byte[seq.length + data.length + 1];
        switch (flag)
        {
            // sending packet to receiver
            case 0:
                packet[0] = 0;
                break;
            // sending ack to sender
            case 1:
                packet[0] = 1;
                break;
            // sending last packet to receiver
            case 2:
                packet[0] = 2;
                break;
        }
        System.arraycopy(seq, 0, packet, 1, seq.length);
        System.arraycopy(data, 0, packet, seq.length + 1, data.length);
        return packet;
    }

    /***
     * This method decodes a 2 byte array as an unsigned int then signs it so we don't have problems with having a leading 1.
     * @param b1
     * @param b2
     * @return signed sequence number.
     */
    private int decodeSeq(byte b1, byte b2)
    {
        return (((b1 & 0xff) << 8) | (b2 & 0xff));
    }

    @Override
    public boolean setMode(int var1)
    {
        if (var1 == 0 || var1 == 1)
        {
            mode = var1;
            return true;
        }
        return false;
    }

    @Override
    public int getMode()
    {
        return mode;
    }

    @Override
    public boolean setModeParameter(long var1)
    {
        if (mode == 1 && var1 > 2)
        {
            windowSize = var1;
            return true;
        }
        return false;

    }

    @Override
    public long getModeParameter()
    {
        return windowSize;
    }

    @Override
    public void setFilename(String var1)
    {
        filename = var1;
    }

    @Override
    public String getFilename()
    {
        return filename;
    }

    @Override
    public boolean setTimeout(long var1)
    {
        if (var1 > 0)
        {
            timeout = var1;
            return true;
        }
        return false;
    }

    @Override
    public long getTimeout()
    {
        return timeout;
    }

    @Override
    public boolean setLocalPort(int var1)
    {
        if (var1 > 1024 && var1 < 65536)
        {
            port = var1;
            return true;
        }
        return false;
    }

    @Override
    public int getLocalPort()
    {
        return port;
    }

    @Override
    public boolean setReceiver(InetSocketAddress var1)
    {
        receiver = var1;
        return true;
    }

    @Override
    public InetSocketAddress getReceiver()
    {
        return receiver;
    }


    @Override
    public boolean sendFile()
    {
        if (filename == null )//|| receiver == null)
        {
//            System.out.println("\n[SENDER]No file and/or receiver specified.");
            return false;
        }
        File file = new File(filename);
        try
        {
            UDPSocket socket = new UDPSocket(port);

            // read entire file into byte buffer. This should be changed so you don't have to read the entire file into memory.
            byte [] fileBuffer = Files.readAllBytes(file.toPath());
            float fileSize = fileBuffer.length;
            int dataSize = socket.getSendBufferSize() - SEQSIZE - 1;
            int numberOfPackets = (int) Math.ceil(fileSize / dataSize);
            allPackets = new ArrayList<>();
            for (int i = 0; i < numberOfPackets; i++)
            {
                // case 1: where we are at the beginning or the middle of the file
                if ((i + 1) * (dataSize) <= fileSize)
                {
                    byte [] temp = new byte[dataSize];
                    System.arraycopy(fileBuffer, i * dataSize, temp, 0, temp.length);
                    allPackets.add(packPacket(temp, i, 0));
                }
                // case 2: where this is the last packet of the file
                else
                {
                    byte [] temp = new byte[(int)fileSize - (i * dataSize)];
                    System.arraycopy(fileBuffer, i * dataSize, temp, 0, temp.length);
                    allPackets.add(packPacket(temp, i, 2)); // set flag to 2 to mean this is the last packet of the file
                }
            }
            outstandingFrames = mode == 1 ? (int)((float)windowSize/socket.getSendBufferSize()) : 1;
            int headPntr = 0;

            // start ack listener
            new Thread()
            {
                public void run()
                {
                    ACKListener(socket);
                }
            }.start();

            // start sending packets
            while (true)
            {
                lastReceivedLock.lock();
                if (lastReceived >= allPackets.size() - 1)
                {
                    lastReceivedLock.unlock();
                    break;
                }
                lastReceivedLock.unlock();
                for (int i = 0; i < outstandingFrames; i++)
                {
                    if (headPntr + i >= numberOfPackets || headPntr + i <= packetsSentUpTo)
                    {
                        continue;
                    }
                    byte [] temp = allPackets.get(headPntr + i);
                    DatagramPacket dpacket = new DatagramPacket(temp, temp.length, receiver.getAddress(), receiver.getPort());
//                    System.out.println("\n[SENDER]Sending packet " + (headPntr + i));
                    socketLock.lock();
                    socket.send(dpacket);
                    socketLock.unlock();
                    packetsSentUpTo = headPntr + i;

                    // start timer thread
                    new Thread()
                    {
                        public void run()
                        {
                            timer(socket, dpacket);
                        }
                    }.start();
                }
                lastReceivedLock.lock();
                headPntr = lastReceived != -1 ? lastReceived + 1 : 0;
                lastReceivedLock.unlock();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }
//        System.out.println("\n[SENDER]Exiting");
        isTransmissionComplete = true;
        return true;
    }

    /***
     * This method is used by one ACK receiver thread updating the
     * @param socket
     */
    private void ACKListener(UDPSocket socket)
    {
        while (true)
        {
            try
            {
                DatagramPacket pckt = new DatagramPacket(new byte[ACKSIZE], ACKSIZE);
//                System.out.println("\n[ACK LISTENER]Listening for ack");
                socket.receive(pckt);
                lastReceivedLock.lock();
                lastReceived = decodeSeq(pckt.getData()[1], pckt.getData()[2]);
                lastReceivedLock.unlock();
//                System.out.println("\n[ACK LISTENER]Received ack " + decodeSeq(pckt.getData()[1], pckt.getData()[2]));
                isTransmissionComplete = decodeSeq(pckt.getData()[1], pckt.getData()[2]) == allPackets.size()-1;
                if (isTransmissionComplete)
                {
//                    System.out.println("\n[ACK LISTENER]Exiting");
                    Thread.currentThread().stop();
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    /***
     * This method should be called with a new thread for each packet. It will sleep for the timeout time
     * then it will check if an ack for the packet has been received if not it will resend the packet and sleep again.
     * @param socket
     * @param packet
     */
    private void timer(UDPSocket socket, DatagramPacket packet)
    {
//        System.out.println("[TIMER]Timer started for packet " + decodeSeq(packet.getData()[1], packet.getData()[2]));
        while (true)
        {
            try
            {
                Thread.sleep(timeout);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            if (lastReceived >= decodeSeq(packet.getData()[1], packet.getData()[2]))
            {
//                System.out.println("[TIMER]Packet " + decodeSeq(packet.getData()[1], packet.getData()[2]) + " received killing timer.");
                Thread.currentThread().stop();
                throw new RuntimeException();
            }
            socketLock.lock();
            try
            {
//                System.out.println("[TIMER]Resending packet " + decodeSeq(packet.getData()[1], packet.getData()[2]));
                socket.send(packet);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            finally
            {
                socketLock.unlock();
            }
        }
    }


    // Globals:
    private int mode = 0;
    private int port = 12987;
    private long timeout = 1000;
    private long windowSize = 256;
    private int outstandingFrames = 1;
    private int lastReceived = -1;
    private int packetsSentUpTo = -1;
    private String ip = "localhost";
    private String filename = null;
    private InetSocketAddress receiver = null;
    private ArrayList<byte []> allPackets = null;
    private final int SEQSIZE = 2;
    private final int ACKSIZE = 4;
    private boolean isTransmissionComplete = false;
    private ReentrantLock socketLock = new ReentrantLock();
    private ReentrantLock lastReceivedLock = new ReentrantLock();
}
