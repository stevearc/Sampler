SampleInstrument {
  var <server, <indexBuffer, <pitchToBuffer;
  // The indexBuffer is a 2-channel buffer that is a lookup table. Each index in
  // the buffer is for a frequency, and the lookup value will be buffer numbers
  // of the two closest buffers in this instrument by pitch. For example, if
  // there are two buffers of pitch 220hz (buffer 1) and 440hz (buffer 2), the
  // structure will function like this:
  //   indexBuffer.get(100) -> [1, 1]  // at 100hz we want buffer 1 in both slots
  //   indexBuffer.get(300) -> [1, 2]  // at 300hz we want both buffers
  //   indexBuffer.get(440) -> [2, 2]  // at 440hz we want buffer 2 in both slots
  // This is passed in as `buf` to the \samplerPlayBuf synth

  *new { |server|
    ^super.new.init(server);
  }
  init { |inServer|
    server = inServer;
    pitchToBuffer = Dictionary.new;
  }

  numChannels {
    pitchToBuffer.do { |buf|
      ^buf.numChannels;
    }
  }

  free {
    pitchToBuffer.do { |b|
      b.free;
    };
    pitchToBuffer = Dictionary.new;
    if (indexBuffer.notNil) {
      indexBuffer.free;
    };
    indexBuffer = nil;
  }

  load { |dir|
    PathName(dir).files.do { |file|
      if (file.extension == "aiff") {
        var pitch = file.fileNameWithoutExtension.asInteger;
        var buffer = Buffer.read(server, file.fullPath);
        this.addBuffer(buffer, pitch);
      }
    }
  }

  save { |dir|
    var index;
    File.mkdir(dir);
    // delete all existing aiff files in this dir
    PathName(dir).files.do { |file|
      if (file.extension == "aiff") {
        File.delete(file.fullPath);
      }
    };
    pitchToBuffer.asAssociations.do { |a|
      var filename = "" ++ a.key ++ ".aiff";
      a.value.write(dir ++ "/" ++ filename);
    };
  }

  addBuffer { |buffer, pitch|
    var maxPitch, pairs;
    if (pitchToBuffer[pitch].notNil) {
      pitchToBuffer[pitch].free;
      pitchToBuffer[pitch] = nil;
    };
    pitchToBuffer.do { |buf|
      if (buf.numChannels != buffer.numChannels) {
        ("Error: trying to add " ++ buffer.numChannels
          ++ " channel buffer to an instrument with "
          ++ buf.numChannels ++ " channels").throw;
      };
    };
    pitchToBuffer[pitch] = buffer;
    pairs = pitchToBuffer.asAssociations;
    if (indexBuffer.notNil) {
      indexBuffer.free;
    };
    // Sort by ascending pitch
    pairs.sort({ |a, b| a.key < b.key});
    maxPitch = pairs[pairs.size - 1].key;
    indexBuffer = Buffer.alloc(server, maxPitch, 2);
    fork {
      var data, idx = 0,
        lowBuf = pairs[0].value.bufnum,
        hiBuf = pairs[0].value.bufnum;
      data = Array.fill(2*maxPitch, {|i|
        var freq = (i/2) + 1;
        if (idx < pairs.size and: (freq >= pairs.clipAt(idx).key)) {
          idx = idx + 1;
          lowBuf = hiBuf;
          hiBuf = pairs.clipAt(idx).value.bufnum;
        };
        if (i % 2 == 0) {
          lowBuf
        } {
          hiBuf
        }
      });
      server.sync;
      indexBuffer.sendCollection(data);
    }
  }
}
