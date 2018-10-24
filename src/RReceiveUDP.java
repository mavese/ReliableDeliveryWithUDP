import edu.utulsa.unet.RReceiveUDPI;
import edu.utulsa.unet.UDPSocket;

import java.io.*;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class RReceiveUDP implements RReceiveUDPI
{
    /***
     * The purpose of this class is to be able to return multiple byte arrays with the unpackPacket method.
     */
    public class Packet
    {
        public Packet(int dataSize)
        {
            seqNum = new byte[SEQSIZE];
            data = new byte[dataSize];
        }
        public byte [] seqNum;
        public byte [] data;
        public byte flag;
    }

    /***
     * This method functions correctly. This will unpack a packet into sequence number and data.
     * @param buffer
     * @return
     */
    private Packet unpackPacket(byte [] buffer)
    {
        Packet packet = new Packet(buffer.length - SEQSIZE - 1);
        for (int i = 0; i < buffer.length; i++)
        {
            if (i == 0)
            {
                packet.flag = buffer[i];
            }
            else if (i < SEQSIZE + 1)
            {
                packet.seqNum[i - 1] = buffer[i];
            }
            else
            {
                packet.data[i - SEQSIZE - 1] = buffer[i];
            }
        }
        return packet;
    }

    private byte [] createACK(byte [] data, int number)
    {
        int flag = 1;
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
        }
        System.arraycopy(seq, 0, packet, 1, seq.length);
        System.arraycopy(data, 0, packet, seq.length + 1, data.length);
        return packet;
    }

    /***
     * This method decodes a 2 byte array as an unsigned int then signs it so we don't have problems with having a leading 1.
     * @param seq
     * @return signed sequence number.
     */
    private int decodeSeq(byte [] seq)
    {
        return (((seq[0] & 0xff) << 8) | (seq[1] & 0xff));
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
    public boolean receiveFile()
    {
        receivedPackets = new ArrayList<>();
        try
        {
            UDPSocket socket = new UDPSocket(port);
            while (true)
            {
                byte[] buffer = new byte[socket.getSendBufferSize()];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
//                System.out.println("\n[RECEIVER]Waiting for packet on port " + port);
                socket.receive(datagramPacket);
                Packet packet = unpackPacket(datagramPacket.getData());
//                System.out.println("\n[RECEIVER]Received packet " + decodeSeq(packet.seqNum));
                if (packet.flag == 2)
                {
                    packet = reformatPacket(packet, datagramPacket.getLength() - 3);
                }
                addToList(packet);
                int ackSeq = getACKToSend();
                if (ackSeq != -1)
                {
                    byte [] ACK = createACK(("").getBytes(), ackSeq);
//                    System.out.println("\n[RECEIVER]Sending ack " + ackSeq);
                    socket.send(new DatagramPacket(ACK, ACK.length, datagramPacket.getAddress(), datagramPacket.getPort()));
                }
                lastReceivedPacketLock.lock();
                if (receivedPackets.get(receivedPackets.size()-1).flag == 2 && getACKToSend() == receivedPackets.size()-1)
                {
                    lastReceivedPacketLock.unlock();
//                    System.out.println("\n[RECEIVER]Exiting");
                    break;
                }
                lastReceivedPacketLock.unlock();
            }
        }
        catch (SocketException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        writeToFile();
        return false;
    }

    /***
     * This method adds the packet item to the appropriate spot in the list and if necessary fills any leading values with null.
     * @param packet to add to the list
     */
    private void addToList(Packet packet)
    {
        int ind = decodeSeq(packet.seqNum);
        // Packet is not in the list
        if (!receivedPackets.contains(packet))
        {
            // case 1: just add packet to head of the list
            if (ind == receivedPackets.size())
            {
                receivedPackets.add(packet);
            }
            // case 2: the packet needs to be inserted into a null value in the list
            else if (ind < receivedPackets.size())
            {
                receivedPackets.set(ind, packet);
            }
            // case 3: the packet needs to be inserted in a future value and all leading values must be filled with nulls
            else
            {
                for (int i = receivedPackets.size(); i < ind; i++)
                {
                    receivedPackets.add(i, null);
                }
                receivedPackets.add(ind, packet);
            }
        }
    }

    /***
     * This method gets the appropriate sequence number to ack or -1 if it shouldn't ack anything.
     * @return int represents sequence number to ack or -1
     */
    private int getACKToSend()
    {
        // case 1: send no ack
        if (receivedPackets.size() == 0 || receivedPackets.get(0) == null)
        {
            return -1;
        }
        // case 2: send ack of highest values before a null value
        int i = 0;
        for (i = 0; i < receivedPackets.size(); i++)
        {
            if (receivedPackets.get(i) == null)
            {
                break;
            }
        }
        lastReceivedPacketLock.lock();
        lastReceivedPacket = --i;
        lastReceivedPacketLock.unlock();
        return i;
    }

    private void writeToFile()
    {
        File file = new File(filename);
        try
        {
            OutputStream os = new FileOutputStream(file);
            for (Packet receivedPacket : receivedPackets)
            {
                for (byte datum : receivedPacket.data)
                {
                    os.write(datum);
                }
            }
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * This method reformats the last packet to truncate trailing 0's
     * @param packet
     * @param length
     * @return
     */
    private Packet reformatPacket(Packet packet, int length)
    {
        byte [] data = new byte[length];
        System.arraycopy(packet.data, 0, data, 0, length);
        Packet rPacket = new Packet(length);
        rPacket.flag = packet.flag;
        rPacket.seqNum = packet.seqNum;
        rPacket.data = data;
        return rPacket;
    }


    // Globals:
    private int lastReceivedPacket = -1;
    private int mode = 0;
    private int port = 12987;
    private long windowSize = 256;
    private int outstandingFrames = 1;
    private String ip = "localhost";
    private String filename = null;
    private ArrayList<Packet> receivedPackets;
    private final int SEQSIZE = 2;
    private ReentrantLock lastReceivedPacketLock = new ReentrantLock();
}
