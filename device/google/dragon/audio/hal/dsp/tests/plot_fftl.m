% Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
% Use of this source code is governed by a BSD-style license that can be
% found in the LICENSE file.
%
% This is an octave script.
% It reads impulse response from "ir.dat" and plots frequency response.
% Both x-axis and y-axis is in log scale.
h=load("ir.dat");
N=columns(h);
K=rows(h)/2;
NQ=44100/2;
% This tries to match the labels in the audio tuning UI.
xticks=[22050, 11025, 5513, 2756, 1378, 689, 345, 172, 86, 43, 21];
xticklabels={"22050Hz", "11025Hz", "5513Hz", "2756Hz", "1378Hz", \
"689Hz", "345Hz", "172Hz", "86Hz", "43Hz", "21Hz"};
yticks=[18,12,6,0,-6,-12,-18,-24];
yticklabels={"18dB","12dB","6dB","0dB","-6dB","-12dB","-18dB","-24dB"};
xyrange=[21,22050,-24,18];
xrange=[21,22050];

for i=1:N
  figure(i);
  title('fftl');
  fr = fft(h(:,i))(1:K);
  subplot(2, 1, 1);
  semilogx(NQ*(1:K)/K, 20*log10(abs(fr)));
  xlabel('Frequency'), ylabel('Magnitude'), grid;
  set (gca, "xtick", xticks);
  set (gca, "xticklabel", xticklabels);
  set (gca, "ytick", yticks);
  set (gca, "yticklabel", yticklabels);
  axis(xyrange);
  subplot(2, 1, 2);
  semilogx(NQ*(1:K)/K,180/pi*unwrap(angle(fr)));
  xlabel('Frequency'), ylabel('Phase (degrees)'), grid;
  set (gca, "xtick", xticks);
  set (gca, "xticklabel", xticklabels);
  axis(xrange);
end
pause
