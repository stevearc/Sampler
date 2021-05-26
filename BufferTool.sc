BufferTool {
  *trimSilence { |buffer, threshold=0.05, callback|
    var newBuf = Buffer.alloc(buffer.server, buffer.numFrames, buffer.numChannels);
    var synth = Synth("samplerTrimSilence" ++ buffer.numChannels,
      [\buf, buffer, \outbuf, newBuf, \threshold, threshold]);
    synth.onFree({
      callback.value(newBuf);
    });
  }

  *detectPitch { |buffer, pitchConfidenceThreshold=0.9, callback|
    var pitchBuffer = this.allocPitchBuffer(buffer);
    var synth = Synth("samplerDetectPitch" ++ buffer.numChannels,
      [\buf, buffer, \outbuf, pitchBuffer, \threshold, pitchConfidenceThreshold]);
    synth.onFree({
      this.analyzePitchBuffer(pitchBuffer, { |pitch|
        pitchBuffer.free;
        callback.value(pitch);
      });
    });
  }

  *allocPitchBuffer { |buffer|
    // The 32 is what I've found to be the rough ratio from control rate to audio rate
    ^Buffer.alloc(buffer.server, buffer.numFrames/(32*buffer.numChannels));
  }

  // This is pretty basic right now. It just find the most common non-zero
  // detected pitch and assumes that's the pitch of the sample.
  *analyzePitchBuffer { |buffer, callback|
    buffer.loadToFloatArray(0, action: { |data|
      var results = ();
      var pitch, maxCount = 0;
      data.do { |v|
        v = v.round(1);
        if (v != 0) {
          results[v] = (results[v] ? 0) + 1;
        };
      };
      results.asAssociations.do { |assoc|
        if (assoc.value > maxCount) {
          maxCount = assoc.value;
          pitch = assoc.key;
        }
      };
      callback.value(pitch.asInteger);
    });
  }

}
