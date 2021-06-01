+ Sampler {
  loadSynthDefs {
    server.waitForBoot({
      (1..2).do { |numChannels|
        var panFunc;
        if (numChannels == 1) {
          panFunc = { |in, pan=0| Pan2.ar(in, pan) };
        } {
          panFunc = { |in, pan=0| Balance2.ar(in[0], in[1], pan) };
        };

        SynthDef("samplerDetectPitch" ++ numChannels, {
          |buf=0, outbuf=0, threshold=0.9|
          var freq, hasFreq;
          var sig = PlayBuf.ar(numChannels, buf, BufRateScale.ir(buf), loop: 0, doneAction: Done.freeSelf);
          sig = Mix(sig);
          # freq, hasFreq = Tartini.kr(sig);
          RecordBuf.kr(freq, outbuf, run: hasFreq>threshold, loop: 0);
        }).add;

        // Multisampler concept taken from https://doc.sccode.org/Tutorials/A-Practical-Guide/PG_Cookbook05_Using_Samples.html
        2.do { |i|
          var name = "samplerPlayBuf";
          var playBuf;
          if (i == 1) {
            name = name ++ "Sus";
            // We want one variant that waits for the ASR envelope to complete
            // before freeing itself, otherwise using it with Pbind can cause
            // error messages about the node already being freed
            playBuf = { |instBuf, freq, bufFreq|
              PlayBuf.ar(numChannels, instBuf, BufRateScale.ir(instBuf) * freq / bufFreq, loop: 0);
            };
          } {
            playBuf = { |instBuf, freq, bufFreq|
              PlayBuf.ar(numChannels, instBuf, BufRateScale.ir(instBuf) * freq / bufFreq, loop: 0, doneAction: Done.freeSelf);
            };
          };
          SynthDef(name ++ numChannels, {
            |out, buf, pitch, freq = 440, amp = 1, atk=0.001, rel=0.1, atkCurve=(-4), relCurve=(-4), gate=1|
            var sig;
            var env = EnvGen.kr(Env.asr(atk, 1, rel, [atkCurve, relCurve]),
              gate: gate, doneAction: Done.freeSelf);
            // Index into the SampleInstrument indexBuffer with the desired
            // frequency (with some math because it's a 2-channel buffer).
            // That will give us the [low, high] buffer numbers
            var instBuf = Index.kr(buf, [freq*2, freq*2+1]);
            var bufFreq = Index.kr(pitch, instBuf);
            var xfade = ((freq - bufFreq[0]) / (bufFreq[1] - bufFreq[0]))
              .madd(2, -1)
              .clip(-1,1);
            sig = SynthDef.wrap(playBuf,  prependArgs: [instBuf, freq, bufFreq]);
            sig = env * XFade2.ar(sig[0], sig[1], xfade, amp);
            sig = SynthDef.wrap(panFunc,  prependArgs: [sig]);
            Out.ar(out, sig);
          }).add;
        };

        // Trims silence from the start of a buffer
        SynthDef("samplerTrimSilence" ++ numChannels, { |buf, outbuf, threshold=0.05|
          var sig = PlayBuf.ar(numChannels, buf, BufRateScale.ir(buf),
            loop: 0, doneAction: Done.freeSelf);
          var amp = Amplitude.ar(Mix(sig));
          var run = Gate.ar(DC.ar(1), amp - threshold);
          RecordBuf.ar(sig, outbuf, run: run, loop: 0);
        }).add;

        SynthDef("samplerRecord" ++ numChannels, { |inbus, outbuf, pitchBuf, run=0, ampThreshold=0.05, pitchThreshold=0.9|
          var freq, hasFreq;
          var sig = SoundIn.ar(Array.series(numChannels, inbus));
          var mixed = Mix(sig);
          var amp = Amplitude.ar(mixed);
          // Don't start recording until we actually get an audio signal
          var doRecord = run * Gate.ar(DC.ar(1), run * (amp - ampThreshold));
          # freq, hasFreq = Tartini.kr(mixed);
          RecordBuf.ar(sig, outbuf, run: doRecord, loop: 0, doneAction: Done.freeSelf);
          RecordBuf.kr(freq, pitchBuf, run: doRecord*(hasFreq>pitchThreshold), loop: 0);
        }).add;
      }
    });
  }
}
