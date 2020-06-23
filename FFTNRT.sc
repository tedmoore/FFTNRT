FFTNRT {

	*fft {
		arg filePath,  action, fftSize = 2048, overlap = 2;
		SoundFile.use(filePath,{
			arg sf;
			var data, window, frames, currentSample = 0,hopSamples, fft, imag;

			hopSamples = fftSize / overlap;

			data = FloatArray.newClear(sf.numFrames * sf.numChannels);
			sf.readData(data);

			// sum to mono
			// TODO: process stereo
			if(sf.numChannels > 1,{
				data = sf.numFrames.collect({
					arg frameI;
					var val = 0;
					sf.numChannels.do({
						arg chanI;
						val = val + data[(frameI * sf.numChannels) + chanI];
					});
					val;
				});
			});

			//data = data.clump(sf.numChannels).flop;

			frames = [];
			while({currentSample < data.size},{
				var newFrame,endIndex;
				//currentSample.postln;
				endIndex = ((currentSample + fftSize).round(1)-1).asInteger;
				endIndex = min(endIndex,data.size-1);
				//endIndex.postln;
				newFrame = data[currentSample..endIndex];
				if(newFrame.size < fftSize,{
					newFrame = newFrame * Array.fill(newFrame.size,{arg i; i.linlin(0,newFrame.size-1,1,0)});
					newFrame = newFrame.addAll(0.dup(fftSize-newFrame.size));
				});
				//newFrame.size.postln;
				frames = frames.add(newFrame);

				currentSample = (currentSample + hopSamples).round(1).asInteger;
			});

			//frames.dopostln;

			// no overlap
			/*	data = data.clump(~fftSize);
			if(data.last.size != ~fftSize,{
			data.removeAt(data.size-1);
			});*/

			// gotta window this shit
			window = Signal.hanningWindow(fftSize);

			imag = Signal.newClear(fftSize);
			fft = frames.collect({
				arg frame;
				var fft;
				//frame.size.postln;
				frame = frame * window;
				fft = fft(frame,imag,Signal.fftCosTable(fftSize));
				frame = ();
				//fft.postln;
				frame.mag = fft.magnitude[0..((fftSize * 0.5) - 1).asInteger];
				frame.phs = fft.angle[0..((fftSize * 0.5) - 1).asInteger];
				[frame.mag,frame.phs];
			});

			action.value(fft);
		});
	}

	*ifft {
		arg fft, action, fftSize = 2048, overlap = 2, ampMul = 1;
		var nFrames = ((fft.size / overlap) + 1) * fftSize;
		var newAudio = FloatArray.newClear(nFrames);
		var hopSamples = fftSize / overlap;
		var adderArray = (0..(fftSize-1));
		var window = Signal.hanningWindow(fftSize);
		fft.do({
			arg frame, index;
			var startSample = (index * hopSamples);
			"index % of %".format(index,fft.size).postln;
			//"start sample: %".format(startSample).postln;
			frame = Polar(frame.mag.asList.mirror2,frame.phs.asList.mirror2).asComplex;
			frame = ifft(frame.real,frame.imag,Signal.fftCosTable(fftSize));
			/*		"frame.real size = %".format(frame.real.size).postln;
			"newAudio length = %".format(newAudio.size).postln;
			"".postln;*/
			//newAudio.putEach(adderArray + startSample,frame.real);
			frame.real = frame.real * window * ampMul;
			fftSize.do({
				arg jdex;
				newAudio[startSample + jdex] = newAudio[startSample + jdex] + frame.real[jdex];
			});
		});
		action.value(newAudio);
	}
}