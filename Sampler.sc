Sampler {
  classvar <>default;
  var <server, <instruments, <bufNumToPitch;
  *initClass {
    default = this.new(Server.default);
  }
  *bufNumToPitch { ^Sampler.default.bufNumToPitch }
  *new { |server|
    ^super.new.init(server);
  }
  init { |inServer|
    instruments = Dictionary.new;
    server = inServer;
    server.waitForBoot({
      bufNumToPitch = Buffer.alloc(server, server.options.numBuffers);
    });
    this.loadSynthDefs;
  }

  *instrumentBuffer { |symbol| ^Sampler.default.instrumentBuffer(symbol) }
  instrumentBuffer { |symbol|
    var instrument = instruments[symbol];
    ^instrument.indexBuffer;
  }

  *addAndTrimBuffer { |symbol, buffer, silenceThreshold=0.05, pitchConfidenceThreshold=0.9, callback|
    Sampler.default.addAndTrimBuffer(symbol, buffer, silenceThreshold, pitchConfidenceThreshold, callback) }
  addAndTrimBuffer { |symbol, buffer, silenceThreshold=0.05, pitchConfidenceThreshold=0.9, callback|
    var pitch;
    var instrumentBuf;
    var addBuffer = {
      if (pitch.notNil and: instrumentBuf.notNil) {
        callback.value(this.addPitchedBuffer(symbol, instrumentBuf, pitch));
      };
    };
    BufferTool.trimSilence(buffer, silenceThreshold, { |newBuf|
      instrumentBuf = newBuf;
      addBuffer.value;
    });
    BufferTool.detectPitch(buffer, pitchConfidenceThreshold, { |detectedPitch|
      pitch = detectedPitch;
      addBuffer.value;
    });
  }

  *addPitchedBuffer { |symbol, buffer, pitch|
    Sampler.default.addPitchedBuffer(symbol, buffer, pitch) }
  addPitchedBuffer { |symbol, buffer, pitch|
    var instrument = instruments[symbol];
    if (instrument.isNil) {
      instruments[symbol] = instrument = SampleInstrument(server);
    };
    bufNumToPitch.set(buffer.bufnum, pitch);
    instrument.addBuffer(buffer, pitch);
    ^instrument;
  }

  *addBuffer { |symbol, buffer, pitchConfidenceThreshold=0.9, callback|
    Sampler.default.addBuffer(symbol, buffer, pitchConfidenceThreshold, callback) }
  addBuffer { |symbol, buffer, pitchConfidenceThreshold=0.9, callback|
    BufferTool.detectPitch(buffer, pitchConfidenceThreshold, { |pitch|
      callback.value(this.addPitchedBuffer(symbol, buffer, pitch));
    });
  }

  *record {|symbol, seconds=4, countdown=3, bus=0, numChannels=1, silenceThreshold=0.05, callback=nil|
    Sampler.default.record(symbol, seconds, countdown, bus, numChannels, silenceThreshold, callback)}
  record {|symbol, seconds=4, countdown=3, bus=0, numChannels=1, silenceThreshold=0.05, callback=nil|
    var buffer, pitchBuffer;
    var clock = TempoClock(1);
    buffer = Buffer.alloc(server, server.sampleRate * seconds, numChannels);
    pitchBuffer = BufferTool.allocPitchBuffer(buffer);
    fork {
      var synth = Synth("samplerRecord"++numChannels,
        [\inbus, 0, \outbuf, buffer, \pitchBuf, pitchBuffer,
        \ampThreshold, silenceThreshold],
        target: RootNode(server), addAction: \addToTail);
      server.sync;
      synth.onFree({
        BufferTool.analyzePitchBuffer(pitchBuffer, { |pitch|
          pitchBuffer.free;
          ("Recorded sample at " ++ pitch ++ "hz").postln;
          callback.value(this.addPitchedBuffer(symbol, buffer, pitch));
        });
      });
      if (countdown <= 0) {
        synth.set(\run, 1);
        "Recording".postln;
      } {
        var countdownTime = clock.nextTimeOnGrid(1, -1*countdown);
        countdown.do { |i|
          clock.schedAbs(countdownTime + i, {
            (countdown - i).postln;
            nil;
          });
        };
        clock.schedAbs(countdownTime + countdown, {
          synth.set(\run, 1);
          "Recording".postln;
          nil;
        });
      };
    };
  }

  *synth { |symbol, args, target, addAction=\addToHead|
    ^Sampler.default.synth(symbol, args, target, addAction) }
  synth { |symbol, args, target, addAction=\addToHead|
    var instrument = instruments[symbol];
    ^Synth("samplerPlayBuf" ++ instrument.numChannels, [
      \pitch, bufNumToPitch,
      \buf, instrument.indexBuffer,
    ] ++ args, target, addAction);
  }

  *bind { |symbol| ^Sampler.default.bind(symbol) }
  bind { |symbol|
    var instrument = instruments[symbol];
    ^[
      \instrument, "samplerPlayBufSus" ++ instrument.numChannels,
      \pitch, bufNumToPitch,
      \buf, instrument.indexBuffer,
    ];
  }

  *save { |symbol, dir| Sampler.default.save(symbol, dir) }
  save { |symbol, dir|
    instruments[symbol].save(dir ++ "/" ++ symbol);
  }

  *saveAll { |dir| Sampler.default.saveAll(dir) }
  saveAll { |dir|
    instruments.keys.do { |key|
      this.save(key, dir);
    }
  }

  *load { |symbol, dir| Sampler.default.load(symbol, dir) }
  load { |symbol, dir|
    var instrument = SampleInstrument.new(server);
    instruments[symbol] = instrument;
    instrument.load(dir);
    instrument.pitchToBuffer.asAssociations.do { |a|
      bufNumToPitch.set(a.value.bufnum, a.key);
    };
  }

  *loadAll { |dir| Sampler.default.loadAll(dir) }
  loadAll { |dir|
    PathName(dir).folders.do { |folder|
      this.load(folder.folderName.asSymbol, folder.fullPath);
    }
  }

  *free { |symbol| Sampler.default.free(symbol) }
  free { |symbol|
    instruments[symbol].free;
    instruments[symbol] = nil;
  }

  *freeAll { Sampler.default.freeAll }
  freeAll {
    instruments.keys.do { |key|
      this.free(key);
    }
  }
}
