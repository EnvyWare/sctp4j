/*
 * Copyright 2017 pi.pe gmbh .
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package pe.pi.sctp4j.sctp.messages;

import pe.pi.sctp4j.sctp.messages.params.RequestedHMACAlgorithmParameter;
import pe.pi.sctp4j.sctp.messages.params.AddIncomingStreamsRequestParameter;
import pe.pi.sctp4j.sctp.messages.params.AddOutgoingStreamsRequestParameter;
import pe.pi.sctp4j.sctp.messages.params.ReconfigurationResponseParameter;
import pe.pi.sctp4j.sctp.messages.params.SSNTSNResetRequestParameter;
import pe.pi.sctp4j.sctp.messages.params.CookiePreservative;
import pe.pi.sctp4j.sctp.messages.params.UnrecognizedParameters;
import pe.pi.sctp4j.sctp.messages.params.StateCookie;
import pe.pi.sctp4j.sctp.messages.params.IPv6Address;
import pe.pi.sctp4j.sctp.messages.params.IPv4Address;
import pe.pi.sctp4j.sctp.messages.exceptions.SctpPacketFormatException;
import pe.pi.sctp4j.sctp.messages.params.HostNameAddress;
import pe.pi.sctp4j.sctp.messages.params.IncomingSSNResetRequestParameter;
import pe.pi.sctp4j.sctp.messages.params.KnownError;
import pe.pi.sctp4j.sctp.messages.params.KnownParam;
import pe.pi.sctp4j.sctp.messages.params.OutgoingSSNResetRequestParameter;
import pe.pi.sctp4j.sctp.messages.params.ProtocolViolationError;
import pe.pi.sctp4j.sctp.messages.params.StaleCookieError;
import pe.pi.sctp4j.sctp.messages.params.SupportedAddressTypes;
import pe.pi.sctp4j.sctp.messages.params.Unknown;
import pe.pi.sctp4j.sctp.messages.params.VariableParam;
import com.phono.srtplight.Log;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Westhawk Ltd<thp@westhawk.co.uk>
 */
public abstract class Chunk {
    /*
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |   Chunk Type  | Chunk  Flags  |        Chunk Length           |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     \                                                               \
     /                          Chunk Value                          /
     \                                                               \
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */

    public final static int DATA = 0;
    public final static int INIT = 1;
    public final static int INITACK = 2;
    public final static int SACK = 3;
    public final static int HEARTBEAT = 4;
    public final static int HEARTBEAT_ACK = 5;
    public final static int ABORT = 6;
    public final static int ERROR = 9;
    public final static int COOKIE_ECHO = 10;
    public final static int COOKIE_ACK = 11;
    public final static int SHUTDOWN_COMPLETE = 14;
    public final static int RE_CONFIG = 130;



    static byte TBIT = 1;

    /*
       Chunk Length: 16 bits (unsigned integer)

      This value represents the size of the chunk in bytes, including
      the Chunk Type, Chunk Flags, Chunk Length, and Chunk Value fields.
      Therefore, if the Chunk Value field is zero-length, the Length
      field will be set to 4.  The Chunk Length field does not count any
      chunk padding.
    */
    static Chunk mkChunk(ByteBuffer pkt) {
        Chunk ret = null;
        if (pkt.remaining() >= 4) {
            byte type = pkt.get();
            byte flags = pkt.get();
            int length = pkt.getChar();
            int itype = (int) (0xff & type);
            switch (itype) {
                case DATA:
                    ret = new DataChunk(type, flags, length, pkt);
                    break;
                case INIT:
                    ret = new InitChunk(type, flags, length, pkt);
                    break;
                case SACK:
                    ret = new SackChunk(type, flags, length, pkt);
                    break;
                case INITACK:
                    ret = new InitAckChunk(type, flags, length, pkt);
                    break;
                case COOKIE_ECHO:
                    ret = new CookieEchoChunk(type, flags, length, pkt);
                    break;
                case COOKIE_ACK:
                    ret = new CookieAckChunk(type, flags, length, pkt);
                    break;
                case ABORT:
                    ret = new AbortChunk(type, flags, length, pkt);
                    break;
                case HEARTBEAT:
                    ret = new HeartBeatChunk(type, flags, length, pkt);
                    break;
                case RE_CONFIG:
                    ret = new ReConfigChunk(type, flags, length, pkt);
                    break;
                default:
                    Log.warn("Default chunk type "+itype+" read in ");
                    ret = new Chunk(type, flags, length, pkt) {
                        @Override
                        void putFixedParams(ByteBuffer ret) {
                            return;
                        }
                    };
                    break;

            }
            if (ret != null) {
                if (pkt.hasRemaining()) {
                    int mod = ret.getLength() % 4;
                    if (mod != 0) {
                        for (int pad = mod; pad < 4; pad++) {
                            pkt.get();
                        }
                    }
                }
            }
        }
        return ret;
    }
    /*
     0          - Payload Data (DATA)
     1          - Initiation (INIT)
     2          - Initiation Acknowledgement (INIT ACK)
     3          - Selective Acknowledgement (SACK)
     4          - Heartbeat Request (HEARTBEAT)
     5          - Heartbeat Acknowledgement (HEARTBEAT ACK)
     6          - Abort (ABORT)
     7          - Shutdown (SHUTDOWN)
     8          - Shutdown Acknowledgement (SHUTDOWN ACK)
     9          - Operation Error (ERROR)
     10         - State Cookie (COOKIE ECHO)
     11         - Cookie Acknowledgement (COOKIE ACK)




     Stewart                     Standards Track                    [Page 17]

     RFC 4960          Stream Control Transmission Protocol    September 2007


     12         - Reserved for Explicit Congestion Notification Echo
     (ECNE)
     13         - Reserved for Congestion Window Reduced (CWR)
     14         - Shutdown Complete (SHUTDOWN COMPLETE)
     */
    /*
    
     Chunk Type  Chunk Name
     --------------------------------------------------------------
     0xC1    Address Configuration Change Chunk        (ASCONF)
     0x80    Address Configuration Acknowledgment      (ASCONF-ACK)
    
     +------------+------------------------------------+
     | Chunk Type | Chunk Name                         |
     +------------+------------------------------------+
     | 130        | Re-configuration Chunk (RE-CONFIG) |
     +------------+------------------------------------+
    
     The following new chunk type is defined:

     Chunk Type    Chunk Name
     ------------------------------------------------------
     192 (0xC0)    Forward Cumulative TSN (FORWARD TSN)
     
    
     Chunk Type  Chunk Name
     --------------------------------------------------------------
     0x81    Packet Drop Chunk        (PKTDROP)
     */
 final static Map<Integer, String> _typeLookup
            = Collections.unmodifiableMap(Stream.of(
                    new AbstractMap.SimpleEntry<>(0,"DATA"),
                    new AbstractMap.SimpleEntry<>(1,"INIT"), 
                    new AbstractMap.SimpleEntry<>(2,"INIT ACK"),
                    new AbstractMap.SimpleEntry<>(3,"SACK"), 
                    new AbstractMap.SimpleEntry<>(4,"HEARTBEAT"),
                    new AbstractMap.SimpleEntry<>(5, "HEARTBEAT ACK"),
                    new AbstractMap.SimpleEntry<>(6, "ABORT"),
                    new AbstractMap.SimpleEntry<>(7, "SHUTDOWN"),
                    new AbstractMap.SimpleEntry<>(8, "SHUTDOWN ACK"),
                    new AbstractMap.SimpleEntry<>(9, "ERROR"),
                    new AbstractMap.SimpleEntry<>(10, "COOKIE ECHO"),
                    new AbstractMap.SimpleEntry<>(11, "COOKIE ACK"),
                    new AbstractMap.SimpleEntry<>(12, "ECNE"),
                    new AbstractMap.SimpleEntry<>(13, "CWR"),
                    new AbstractMap.SimpleEntry<>(14, "SHUTDOWN COMPLETE"),
                    new AbstractMap.SimpleEntry<>(15, "AUTH"),
                    new AbstractMap.SimpleEntry<>(0xC1, "ASCONF"),
                    new AbstractMap.SimpleEntry<>(0x80, "ASCONF-ACK"),
                    new AbstractMap.SimpleEntry<>(130, "RE-CONFIG"),
                    new AbstractMap.SimpleEntry<>(192, "FORWARDTSN"),
                    new AbstractMap.SimpleEntry<>(0x81, "PKTDROP")
                                ).collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue())));
    final static Map<String, Integer> __nameMap = _typeLookup.entrySet().stream().collect(Collectors.toMap(
            e -> e.getValue(),
            e -> e.getKey()));
    byte _type;
    byte _flags;
    int _length;
    ByteBuffer _body;
    ArrayList<VariableParam> _varList = new ArrayList<VariableParam>();


    protected Chunk(byte type) {
        _type = type;
    }

    protected Chunk(byte type, byte flags, int length, ByteBuffer pkt) {
        _type = type;
        _flags = flags;
        _length = length;
        /* Copy version 
        byte bb[] = new byte[length -4]; 
        pkt.get(bb);
        _body = ByteBuffer.wrap(bb);
        */
        // or use same data but different bytebuffers wrapping it 
        _body = pkt.slice();
        ((Buffer)_body).limit(length-4);
        Buffer bpkt = (Buffer)pkt;
        bpkt.position(bpkt.position()+(length -4));
    }
// sad ommission in ByteBuffer 

    public static long getUnsignedInt(ByteBuffer bb) {
        return ((long) bb.getInt() & 0xffffffffL);
    }

    public static void putUnsignedInt(ByteBuffer bb, long value) {
        bb.putInt((int) (value & 0xffffffffL));
    }

    void write(ByteBuffer ret) throws SctpPacketFormatException {
        ret.put(_type);
        ret.put(_flags);
        ret.putChar((char) 4); // marker for length;
        Buffer bret = (Buffer) ret;
        putFixedParams(ret);
        int pad = 0;
        if (_varList != null) {
            for (VariableParam v : this._varList) {
                Log.debug("var " + v.getName() + " at " + bret.position());

                ByteBuffer var = ret.slice();
                var.putChar((char) v.getType());
                var.putChar((char) 4); // length holder.
                v.writeBody(var);
                Buffer bvar = (Buffer) var;
                var.putChar(2, (char) bvar.position());
                Log.verb("setting var length to " + bvar.position());
                pad = bvar.position() % 4;
                pad = (pad != 0) ? 4 - pad : 0;
                Log.verb("padding by " + pad);
                bret.position(bret.position() + bvar.position() + pad);
            }
        }
        //System.out.println("un padding by " + pad);
        bret.position(bret.position() - pad);
        // and push the new length into place.
        ret.putChar(2, (char) bret.position());
        //System.out.println("setting chunk length to " + ret.position());
    }

    public String typeLookup() {
        return typeLookup(this._type);
    }

    public static String typeLookup(byte t) {
        Integer k = new Integer((int) ((0xff) & t));
        String ret = _typeLookup.get(k);
        if (ret == null) {
            ret = "unknown(" + (int) ((0xff) & t) + ")";
        }
        return ret;
    }
    public static String chunksToNames(byte[] fse) {
        StringBuffer ret = new StringBuffer();
        for (byte f:fse){
            ret.append(typeLookup(f));
            ret.append(" ");
        }
        return ret.toString();
    }
    public String toString() {
        return "Chunk : type " + typeLookup(_type) + " flags " + Integer.toHexString((0xff) & _flags) + " length = " + _length;
    }

    public int getType() {
        return _type;
    }

    int getLength() {
        return _length;
    }
    /*
    
     1	Heartbeat Info	[RFC4960]
     2-4	Unassigned	
     5	IPv4 Address	[RFC4960]
     6	IPv6 Address	[RFC4960]
     7	State Cookie	[RFC4960]
     8	Unrecognized Parameters	[RFC4960]
     9	Cookie Preservative	[RFC4960]
     10	Unassigned	
     11	Host Name Address	[RFC4960]
     12	Supported Address Types	[RFC4960]
     13	Outgoing SSN Reset Request Parameter	[RFC6525]
     14	Incoming SSN Reset Request Parameter	[RFC6525]
     15	SSN/TSN Reset Request Parameter	[RFC6525]
     16	Re-configuration Response Parameter	[RFC6525]
     17	Add Outgoing Streams Request Parameter	[RFC6525]
     18	Add Incoming Streams Request Parameter	[RFC6525]
     19-32767	Unassigned	
     32768	Reserved for ECN Capable (0x8000)	
     32770	Random (0x8002)	[RFC4805]
     32771	Chunk List (0x8003)	[RFC4895]
     32772	Requested HMAC Algorithm Parameter (0x8004)	[RFC4895]
     32773	Padding (0x8005)	
     32776	Supported Extensions (0x8008)	[RFC5061]
     32777-49151	Unassigned	
     49152	Forward TSN supported (0xC000)	[RFC3758]
     49153	Add IP Address (0xC001)	[RFC5061]
     49154	Delete IP Address (0xC002)	[RFC5061]
     49155	Error Cause Indication (0xC003)	[RFC5061]
     49156	Set Primary Address (0xC004)	[RFC5061]
     49157	Success Indication (0xC005)	[RFC5061]
     49158	Adaptation Layer Indication (0xC006)	[RFC5061]

    
     */

    protected VariableParam readVariable() {
        int type = _body.getChar();
        int len = _body.getChar();
        int blen = len - 4;
        byte[] data = null;
        Unknown var;
        switch (type) {
            case 1:
                var = new HeartbeatInfo(1, "HeartbeatInfo");
                break;
//      2-4	Unassigned	
            case 5:
                var = new IPv4Address(5, "IPv4Address");
                break;
            case 6:
                var = new IPv6Address(6, "IPv6Address");
                break;
            case 7:
                var = new StateCookie(7, "StateCookie");
                break;
            case 8:
                var = new UnrecognizedParameters(8, "UnrecognizedParameters");
                break;
            case 9:
                var = new CookiePreservative(9, "CookiePreservative");
                break;
//      10	Unassigned	
            case 11:
                var = new HostNameAddress(11, "HostNameAddress");
                break;
            case 12:
                var = new SupportedAddressTypes(12, "SupportedAddressTypes");
                break;
            case 13:
                var = new OutgoingSSNResetRequestParameter(13, "OutgoingSSNResetRequestParameter");
                break;
            case 14:
                var = new IncomingSSNResetRequestParameter(14, "IncomingSSNResetRequestParameter");
                break;
            case 15:
                var = new SSNTSNResetRequestParameter(15, "SSNTSNResetRequestParameter");
                break;
            case 16:
                var = new ReconfigurationResponseParameter(16, "ReconfigurationResponseParameter");
                break;
            case 17:
                var = new AddOutgoingStreamsRequestParameter(17, "AddOutgoingStreamsRequestParameter");
                break;
            case 18:
                var = new AddIncomingStreamsRequestParameter(18, "AddIncomingStreamsRequestParameter");
                break;
//      19-32767	Unassigned	
            case 32768:
                var = new Unknown(32768, "ReservedforECNCapable");
                break;
            case 32770:
                var = new RandomParam(32770, "Random");
                break;
            case 32771:
                var = new ChunkListParam(32771, "ChunkList");
                break;
            case 32772:
                var = new RequestedHMACAlgorithmParameter(32772, "RequestedHMACAlgorithmParameter");
                break;
            case 32773:
                var = new Unknown(32773, "Padding");
                break;
            case 32776:
                var = new SupportedExtensions(32776, "SupportedExtensions");
                break;
//      32777-49151	Unassigned	
            case 49152:
                var = new ForwardTSNsupported(49152, "ForwardTSNsupported");
                break;
            case 49153:
                var = new Unknown(49153, "AddIPAddress");
                break;
            case 49154:
                var = new Unknown(49154, "DeleteIPAddress");
                break;
            case 49155:
                var = new Unknown(49155, "ErrorCauseIndication");
                break;
            case 49156:
                var = new Unknown(49156, "SetPrimaryAddress");
                break;
            case 49157:
                var = new Unknown(49157, "SuccessIndication");
                break;
            case 49158:
                var = new Unknown(49158, "AdaptationLayerIndication");
                break;
            default:
                var = new Unknown(-1, "Unknown");
                break;
        }
        try {
            var.readBody(_body, blen);
            Log.debug("variable type " + var.getType() + " name " + var.getName());
        } catch (SctpPacketFormatException ex) {
            Log.error(ex.getMessage());
        }
        if (_body.hasRemaining()) {
            int mod = blen % 4;
            if (mod != 0) {
                for (int pad = mod; pad < 4; pad++) {
                    _body.get();
                }
            }
        }
        return var;
    }

    protected VariableParam readErrorParam() {
        int type = _body.getChar();
        int len = _body.getChar();
        int blen = len - 4;
        byte[] data = null;
        KnownError var = null;
        switch (type) {
            case 1:
                var = new KnownError(1, "InvalidStreamIdentifier");
                break;//[RFC4960]
            case 2:
                var = new KnownError(2, "MissingMandatoryParameter");
                break;//[RFC4960]
            case 3:
                var = new StaleCookieError();
                break;//[RFC4960]
            case 4:
                var = new KnownError(4, "OutofResource");
                break;//[RFC4960]
            case 5:
                var = new KnownError(5, "UnresolvableAddress");
                break;//[RFC4960]
            case 6:
                var = new KnownError(6, "UnrecognizedChunkType");
                break;//[RFC4960]
            case 7:
                var = new KnownError(7, "InvalidMandatoryParameter");
                break;//[RFC4960]
            case 8:
                var = new KnownError(8, "UnrecognizedParameters");
                break;//[RFC4960]
            case 9:
                var = new KnownError(9, "NoUserData");
                break;//[RFC4960]
            case 10:
                var = new KnownError(10, "CookieReceivedWhileShuttingDown");
                break;//[RFC4960]
            case 11:
                var = new KnownError(11, "RestartofanAssociationwithNewAddresses");
                break;//[RFC4960]
            case 12:
                var = new KnownError(12, "UserInitiatedAbort");
                break;//[RFC4460]
            case 13:
                var = new ProtocolViolationError(13, "ProtocolViolation");
                break;//[RFC4460]
// 14-159,Unassigned,
            case 160:
                var = new KnownError(160, "RequesttoDeleteLastRemainingIPAddress");
                break;//[RFC5061]
            case 161:
                var = new KnownError(161, "OperationRefusedDuetoResourceShortage");
                break;//[RFC5061]
            case 162:
                var = new KnownError(162, "RequesttoDeleteSourceIPAddress");
                break;//[RFC5061]
            case 163:
                var = new KnownError(163, "AssociationAbortedduetoillegalASCONF-ACK");
                break;//[RFC5061]
            case 164:
                var = new KnownError(164, "Requestrefused-noauthorization");
                break;//[RFC5061]
// 165-260,Unassigned,
            case 261:
                var = new KnownError(261, "UnsupportedHMACIdentifier");
                break;//[RFC4895]
// 262-65535,Unassigned,
        }
        try {
            var.readBody(_body, blen);
            Log.verb("variable type " + var.getType() + " name " + var.getName());
            Log.verb("additional info " + var.toString());
        } catch (SctpPacketFormatException ex) {
            Log.error(ex.getMessage());
        }
        if (_body.hasRemaining()) {
            int mod = blen % 4;
            if (mod != 0) {
                for (int pad = mod; pad < 4; pad++) {
                    _body.get();
                }
            }
        }
        return var;
    }

    abstract void putFixedParams(ByteBuffer ret);

    public void validate() throws Exception { // todo be more specific in the Exception tree

        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    protected static class HeartbeatInfo extends KnownParam {

        public HeartbeatInfo(int t, String n) {
            super(t, n);
        }
    }

    protected static class ForwardTSNsupported extends KnownParam {

        public ForwardTSNsupported(int t, String n) {
            super(t, n);
        }

    }

    protected static class RandomParam extends KnownParam {

        public RandomParam(int t, String n) {
            super(t, n);
        }

        public String toString() {
            String ret = " random value ";
            ret += Packet.getHex(this.getData());
            return super.toString() + ret;
        }

    }

    protected static class ChunkListParam extends KnownParam {

        public ChunkListParam(int t, String n) {
            super(t, n);
        }

        public String toString() {
            String ret = " ChunksTypes ";
            byte[] data = this.getData();
            for (int i = 0; i < data.length; i++) {
                ret += " " + typeLookup(data[i]);
            }
            return super.toString() + ret;
        }
    }

    protected static class SupportedExtensions extends KnownParam {

        public SupportedExtensions() {
            this(32776, "SupportedExtensions");
        }

        public SupportedExtensions(int t, String n) {
            super(t, n);
        }

        public String toString() {
            String ret = " ChunksTypes ";
            byte[] data = this.getData();
            for (int i = 0; i < data.length; i++) {
                ret += " " + typeLookup(data[i]);
            }
            return super.toString() + ret;
        }
    }

}
