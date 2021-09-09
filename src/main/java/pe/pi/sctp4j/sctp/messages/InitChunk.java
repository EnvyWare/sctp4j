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
import pe.pi.sctp4j.sctp.messages.params.VariableParam;
import com.phono.srtplight.Log;
import java.nio.ByteBuffer;

/**
 *
 * @author tim
 */
public class InitChunk extends Chunk {
    /*
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |   Type = 1    |  Chunk Flags  |      Chunk Length             |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |                         Initiate Tag                          |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |           Advertised Receiver Window Credit (a_rwnd)          |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |  Number of Outbound Streams   |  Number of Inbound Streams    |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     |                          Initial TSN                          |
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     \                                                               \
     /              Optional/Variable-Length Parameters              /
     \                                                               \
     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */

    long _initiateTag;
    long _adRecWinCredit;
    int _numOutStreams;
    int _numInStreams;
    long _initialTSN;
    byte [] _farSupportedExtensions;
    byte [] _farRandom;
    boolean _farForwardTSNsupported;
    byte[] _farHmacs;
    byte[] _farChunks;
    public int _outStreams;

    public InitChunk(){
        super((byte)INIT);
    }
    
    public InitChunk(byte type, byte flags, int length, ByteBuffer pkt) {
        super(type, flags, length, pkt);
        if (_body.remaining() >= 16) {
            _initiateTag = _body.getInt();
            _adRecWinCredit = getUnsignedInt(_body);
            _numOutStreams = _body.getChar();
            _numInStreams = _body.getChar();
            _initialTSN =  getUnsignedInt(_body);
            Log.verb("Init " + this.toString());
            while (_body.hasRemaining()) {
                VariableParam v = readVariable();
                _varList.add(v);
            }
            for (VariableParam v : _varList){
                // now look for variables we are expecting...
                Log.verb("variable of type: "+v.getName()+" "+ v.toString());
                if (v instanceof SupportedExtensions){
                    _farSupportedExtensions = ((SupportedExtensions)v).getData();
                } else if (v instanceof RandomParam){
                    _farRandom = ((RandomParam)v).getData();
                } else if (v instanceof ForwardTSNsupported){
                    _farForwardTSNsupported = true;
                } else if (v instanceof RequestedHMACAlgorithmParameter){
                    _farHmacs = ((RequestedHMACAlgorithmParameter)v).getData();
                } else if (v instanceof ChunkListParam){
                    _farChunks = ((ChunkListParam)v).getData();
                } else {
                    Log.debug("unexpected variable of type: "+v.getName());
                }
            }
        }
    }
    public String toString() {
        String ret = super.toString();
        ret += " initiateTag : " + _initiateTag
                + " adRecWinCredit : " + _adRecWinCredit
                + " numOutStreams : " + _numOutStreams
                + " numInStreams : " + _numInStreams
                + " initialTSN : " + _initialTSN
                + " farForwardTSNsupported : "+_farForwardTSNsupported
                + ((_farSupportedExtensions == null) ?" no supported extensions": " supported extensions are: "+chunksToNames(_farSupportedExtensions));
        return ret;
    }

    @Override
    void putFixedParams(ByteBuffer ret) {
        ret.putInt((int)_initiateTag);
        putUnsignedInt(ret,_adRecWinCredit);
        ret.putChar((char) _numOutStreams);
        ret.putChar((char) _numInStreams);
        Chunk.putUnsignedInt(ret,_initialTSN);
    }

    public int getInitiateTag() {
        return (int)_initiateTag;
    }
    
    public long getAdRecWinCredit(){
        return _adRecWinCredit;
    }
    public int getNumOutStreams(){
        return _numOutStreams;
    }
    public int getNumInStreams(){
        return _numInStreams;
    }
    public long getInitialTSN(){
        return _initialTSN;
    }
    public void setInitialTSN(long tsn){
        _initialTSN = tsn;
    }
    public void setAdRecWinCredit(long credit){
        _adRecWinCredit = credit;
    }
    public void setNumOutStreams(int outn){
        _numOutStreams = outn;
    }
    public void setNumInStreams(int inn){
        _numInStreams = inn;
    }
    public byte [] getFarSupportedExtensions(){
        return _farSupportedExtensions;
    }        

    public void setInitiate(long tag) {
        this._initiateTag = tag;
    }


}
