import java.net.DatagramPacket;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;

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
            System.out.println("No file or receiver specified.");
            return false;
        }
        File file = new File(filename);
        try
        {
            UDPSocket socket = new UDPSocket(2024);

            // read entire file into byte buffer. This should be changed so you don't have to read the entire file into memory.
            byte [] fileBuffer = Files.readAllBytes(file.toPath());
            float fileSize = fileBuffer.length;
            int dataSize = socket.getSendBufferSize() - SEQSIZE - 1;
            int numberOfPackets = (int) Math.ceil(fileSize / dataSize);
            allPackets = new ArrayList<>();
            for (int i = 0; i < numberOfPackets; i++)
            {
                // case 0 where we are at the beginning or the middle of a file
                if ((i + 1) * (dataSize) <= fileSize)
                {
                    byte [] temp = new byte[dataSize];
                    System.arraycopy(fileBuffer, i * dataSize, temp, 0, temp.length);
                    allPackets.add(packPacket(temp, i, 0));
                }
                // case 1 where this is the last packet of the file
                else
                {
                    byte [] temp = new byte[(int)fileSize - (i * dataSize)];
                    System.arraycopy(fileBuffer, i * dataSize, temp, 0, temp.length);
                    allPackets.add(packPacket(temp, i, 0));
                }
            }
            outstandingFrames = (int) ((float)windowSize / packetSize);
            int headPntr = 0;
            boolean isReceived = true;
            while (headPntr < allPackets.size())
            {
                for (int i = 0; i < outstandingFrames; i++)
                {
                    if (headPntr + i >= numberOfPackets)
                    {
                        continue;
                    }
                    byte [] temp = allPackets.get(headPntr + i);
                    socket.send(new DatagramPacket(temp, temp.length, InetAddress.getByName(ip), port));
                    System.out.println("Sent packet " + i);
                }
                byte [] buffer = new byte[ACKSIZE];
                DatagramPacket ack = new DatagramPacket(buffer, buffer.length);
                socket.receive(ack);
                System.out.println("Received ack");
                if (isReceived)
                {
                    ++headPntr;
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }
        return true;
    }


    // Globals:
    private int mode = 0;
    private int packetSize;
    private int port = 12987;
    private long timeout = 1000;
    private long windowSize = 256;
    private int outstandingFrames = 1;
    private int received = -1;
    private String ip = "localhost";
    private String filename = null;
    private InetSocketAddress receiver = null;
    private ArrayList<byte []> allPackets = null;
    private final int SEQSIZE = 2;
    private final int ACKSIZE = 4;

}
