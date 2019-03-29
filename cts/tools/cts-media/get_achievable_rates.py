#!/usr/bin/python
# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import argparse, json, math, re, sys, zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict, namedtuple


class Size(namedtuple('Size', ['width', 'height'])):
  """A namedtuple with width and height fields."""
  def __str__(self):
    return '%dx%d' % (self.width, self.height)

def nicekey(v):
  """Returns a nicer sort key for sorting strings.

  This sorts using lower case, with numbers in numerical order first."""
  key = []
  num = False
  for p in re.split('(\d+)', v.lower()):
    if num:
      key.append(('0', int(p)))
    elif p:
      key.append((p, 0))
    num = not num
  return key + [(v, 0)]

def nice(v):
  """Returns a nicer representation for objects in debug messages.

  Dictionaries are sorted, size is WxH, unicode removed, and floats have 1 digit precision."""
  if isinstance(v, dict):
    return 'dict(' + ', '.join(k + '=' + nice(v) for k, v in sorted(v.items(), key=lambda i: nicekey(i[0]))) + ')'
  if isinstance(v, str):
    return repr(v)
  if isinstance(v, int):
    return str(v)
  if isinstance(v, Size):
    return repr(str(v))
  if isinstance(v, float):
    return '%.1f' % v
  if isinstance(v, type(u'')):
    return repr(str(v))
  raise ValueError(v)

class ResultParser:
  @staticmethod
  def _intify(value):
    """Returns a value converted to int if possible, else the original value."""
    try:
      return int(value)
    except ValueError:
      return value

  def _parseDict(self, value):
    """Parses a MediaFormat from its string representation sans brackets."""
    return dict((k, self._intify(v))
                for k, v in re.findall(r'([^ =]+)=([^ [=]+(?:|\[[^\]]+\]))(?:, |$)', value))

  def _cleanFormat(self, format):
    """Removes internal fields from a parsed MediaFormat."""
    format.pop('what', None)
    format.pop('image-data', None)

  MESSAGE_PATTERN = r'(?P<key>\w+)=(?P<value>\{[^}]*\}|[^ ,{}]+)'

  def _parsePartialResult(self, message_match):
    """Parses a partial test result conforming to the message pattern.

    Returns:
      A tuple of string key and int, string or dict value, where dict has
      string keys mapping to int or string values.
    """
    key, value = message_match.group('key', 'value')
    if value.startswith('{'):
      value = self._parseDict(value[1:-1])
      if key.endswith('Format'):
        self._cleanFormat(value)
    else:
      value = self._intify(value)
    return key, value


def perc(data, p, fn=round):
  """Returns a percentile value from a sorted array.

  Arguments:
    data: sorted data
    p:    percentile value (0-100)
    fn:   method used for rounding the percentile to an integer index in data
  """
  return data[int(fn((len(data) - 1) * p / 100))]


def genXml(data, A=None):
  yield '<?xml version="1.0" encoding="utf-8" ?>'
  yield '<!-- Copyright 2016 The Android Open Source Project'
  yield ''
  yield '     Licensed under the Apache License, Version 2.0 (the "License");'
  yield '     you may not use this file except in compliance with the License.'
  yield '     You may obtain a copy of the License at'
  yield ''
  yield '          http://www.apache.org/licenses/LICENSE-2.0'
  yield ''
  yield '     Unless required by applicable law or agreed to in writing, software'
  yield '     distributed under the License is distributed on an "AS IS" BASIS,'
  yield '     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.'
  yield '     See the License for the specific language governing permissions and'
  yield '     limitations under the License.'
  yield '-->'
  yield ''
  yield '<MediaCodecs>'
  last_section = None
  from collections import namedtuple
  Comp = namedtuple('Comp', 'is_decoder google mime name')
  Result = namedtuple('Result', 'mn mx p95 med geo p5')
  for comp_, cdata in sorted(data.items()):
    comp = Comp(*comp_)
    section = 'Decoders' if comp.is_decoder else 'Encoders'
    if section != last_section:
      if last_section:
        yield '    </%s>' % last_section
      yield '    <%s>' % section
      last_section = section
    yield '        <MediaCodec name="%s" type="%s" update="true">' % (comp.name, comp.mime)
    for size, sdata in sorted(cdata.items()):
      data = sorted(sdata)
      N = len(data)
      mn, mx = data[0], data[-1]

      if N < 20 and not A.ignore:
        raise ValueError("need at least 20 data points for %s size %s; have %s" %
                         (comp.name, size, N))

      TO = 2.2     # tolerance with margin
      T = TO / 1.1 # tolerance without margin

      Final = namedtuple('Final', 'comment c2 var qual')
      lastFinal = None
      for RG in (10, 15, 20, 25, 30, 40, 50):
        P = 50./RG
        quality = 0
        p95, med, p5 = perc(data, P, math.floor), perc(data, 50, round), perc(data, 100 - P, math.ceil)
        geo = math.sqrt(p5 * p95)
        comment = ''
        pub_lo, pub_hi = min(int(p95 * T), round(geo)), max(math.ceil(p5 / T), round(geo))
        if pub_lo > med:
          if pub_lo > med * 1.1:
            quality += 0.5
            comment += ' SLOW'
          pub_lo = int(med)
        if N < 2 * RG:
          comment += ' N=%d' % N
          quality += 2
        RGVAR = False
        if p5 / p95 > T ** 3:
          quality += 3
          RGVAR = True
          if pub_hi > pub_lo * TO:
            quality += 1
            if RG == 10:
              # find best pub_lo and pub_hi
              for i in range(N / 2):
                pub_lo_, pub_hi_ = min(int(data[N / 2 - i - 1] * T), round(geo), int(med)), max(math.ceil(data[N / 2 + i] / T), round(geo))
                if pub_hi_ > pub_lo_ * TO:
                  # ???
                  pub_lo = min(pub_lo, math.ceil(pub_hi_ / TO))
                  break
                pub_lo, pub_hi = pub_lo_, pub_hi_
        if mn < pub_lo / T or mx > pub_hi * T or pub_lo <= pub_hi / T:
          quality += 1
          comment += ' FLAKY('
          if round(mn, 1) < pub_lo / T:
            comment += 'mn=%.1f < ' % mn
          comment += 'RANGE'
          if round(mx, 1) > pub_hi * T:
            comment += ' < mx=%.1f' % mx
          comment += ')'
        if False:
          comment += ' DATA(mn=%1.f p%d=%1.f accept=%1.f-%1.f p50=%1.f p%d=%1.f mx=%1.f)' % (
            mn, 100-P, p95, pub_lo / T, pub_hi * T, med, P, p5, mx)
        var = math.sqrt(p5/p95)
        if p95 < geo / T or p5 > geo * T:
          if RGVAR:
            comment += ' RG.VARIANCE:%.1f' % ((p5/p95) ** (1./3))
          else:
            comment += ' variance:%.1f' % var
        comment = comment.replace('RANGE', '%d - %d' % (math.ceil(pub_lo / T), int(pub_hi * T)))
        c2 = ''
        if N >= 2 * RG:
          c2 += ' N=%d' % N
        if var <= T or p5 / p95 > T ** 3:
          c2 += ' v%d%%=%.1f' % (round(100 - 2 * P), var)
        if A and A.dbg:
          c2 += ' E=%s' % (str(quality))
        if c2:
          c2 = ' <!--%s -->' % c2

        if comment:
          comment = '            <!-- measured %d%%:%d-%d med:%d%s -->' % (round(100 - 2 * P), int(p95), math.ceil(p5), int(round(med)), comment)
        if A and A.dbg: yield '<!-- --> %s%s' % (comment, c2)
        c2 = '            <Limit name="measured-frame-rate-%s" range="%d-%d" />%s' % (size, pub_lo, pub_hi, c2)
        final = Final(comment, c2, var, quality)
        if lastFinal and final.var > lastFinal.var * math.sqrt(1.3):
          if A and A.dbg: yield '<!-- RANGE JUMP -->'
          break
        elif not lastFinal or quality <= lastFinal.qual:
          lastFinal = final
        if N < 2 * RG or quality >= 4:
          break
      comment, c2, var, quality = lastFinal

      if comment:
        yield comment
      yield c2
    yield '        </MediaCodec>'
  if last_section:
    yield '    </%s>' % last_section
  yield '</MediaCodecs>'


class Data:
  def __init__(self):
    self.data = set()
    self.kind = {}
    self.devices = set()
    self.parser = ResultParser()

  def summarize(self, A=None):
    devs = sorted(self.devices)
    #           device  > (not encoder,goog,mime,codec)  >  size > fps
    xmlInfo = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))

    for mime, encoder, goog in sorted(set(self.kind.values())):
      for dev, build, codec, size, num, std, avg, p0, p5, p10, p20, p30, p40, p50, p60, p70, p80, p90, p95, p100 in self.data:
        if self.kind[codec] != (mime, encoder, goog):
          continue

        if p95 > 2: # ignore measurements at or below 2fps
          xmlInfo[dev][(not encoder, goog, mime, codec)][size].append(p95)
        else:
          print >> sys.stderr, "warning: p95 value is suspiciously low: %s" % (
            nice(dict(config=dict(dev=dev, codec=codec, size=str(size), N=num),
                 data=dict(std=std, avg=avg, p0=p0, p5=p5, p10=p10, p20=p20, p30=p30, p40=p40,
                           p50=p50, p60=p60, p70=p70, p80=p80, p90=p90, p95=p95, p100=p100))))
    for dev, ddata in xmlInfo.items():
      outFile = '{}.media_codecs_performance.xml'.format(dev)
      print >> sys.stderr, "generating", outFile
      with open(outFile, "wt") as out:
        for l in genXml(ddata, A=A):
          out.write(l + '\n')
          print l
      print >> sys.stderr, "generated", outFile

  def parse_fmt(self, fmt):
    return self.parser._parseDict(fmt)

  def parse_perf(self, a, device, build):
    def rateFn(i):
      if i is None:
        return i
      elif i == 0:
        return 1e6
      return 1000. / i

    points = ('avg', 'min', 'p5', 'p10', 'p20', 'p30', 'p40', 'p50', 'p60', 'p70', 'p80', 'p90', 'p95', 'max')
    a = dict(a)
    codec = a['codec_name'] + ''
    mime = a['mime_type']
    size = Size(a['width'], a['height'])
    if 'decode_to' in a:
      fmt = self.parse_fmt(a['output_format'])
      ofmt = self.parse_fmt(a['input_format'])
    else:
      fmt = self.parse_fmt(a['input_format'])
      ofmt = self.parse_fmt(a['output_format'])
    size = Size(max(fmt['width'], ofmt['width']), max(fmt['height'], ofmt['height']))

    try:
      prefix = 'time_avg_stats_'
      if prefix + 'stdev' in a and a[prefix + 'avg']:
        stdev = (a[prefix + 'stdev'] * 1e3 / a[prefix + 'avg'] ** 2)
        data = ((device, build, codec, size, a[prefix  + 'num'], stdev) +
                tuple(rateFn(a.get(prefix + i)) for i in points))
        self.data.add(data)
        self.kind[codec] = (mime, 'decode_to' not in a, codec.lower().startswith('omx.google.'))
        self.devices.add(data[0])
    except (KeyError, ZeroDivisionError):
      print >> sys.stderr, a
      raise

  def parse_json(self, json, device, build):
    for test, results in json:
      if test in ("video_encoder_performance", "video_decoder_performance"):
        try:
          if isinstance(results, list) and len(results[0]) and len(results[0][0]) == 2 and len(results[0][0][0]):
            for result in results:
              self.parse_perf(result, device, build)
          else:
            self.parse_perf(results, device, build)
        except KeyboardInterrupt:
          raise

  def parse_result(self, result):
    device, build = '', ''
    if not result.endswith('.zip'):
      print >> sys.stderr, "cannot parse %s" % result
      return

    try:
      with zipfile.ZipFile(result) as zip:
        resultInfo, testInfos = None, []
        for info in zip.infolist():
          if re.search(r'/GenericDeviceInfo.deviceinfo.json$', info.filename):
            resultInfo = info
          elif re.search(r'/Cts(Media|Video)TestCases\.reportlog\.json$', info.filename):
            testInfos.append(info)
        if resultInfo:
          try:
            jsonFile = zip.open(resultInfo)
            jsonData = json.load(jsonFile, encoding='utf-8')
            device, build = jsonData['build_device'], jsonData['build_id']
          except ValueError:
            print >> sys.stderr, "could not parse %s" % resultInfo.filename
        for info in testInfos:
          jsonFile = zip.open(info)
          try:
            jsonData = json.load(jsonFile, encoding='utf-8', object_pairs_hook=lambda items: items)
          except ValueError:
            print >> sys.stderr, "cannot parse JSON in %s" % info.filename
          self.parse_json(jsonData, device, build)

    except zipfile.BadZipfile:
      raise ValueError('bad zipfile')


P = argparse.ArgumentParser("gar_v2")
P.add_argument("--dbg", "-v", action='store_true', help="dump debug info into xml")
P.add_argument("--ignore", "-I", action='store_true', help="ignore minimum sample count")
P.add_argument("result_zip", nargs="*")
A = P.parse_args()

D = Data()
for res in A.result_zip:
  D.parse_result(res)
D.summarize(A=A)
