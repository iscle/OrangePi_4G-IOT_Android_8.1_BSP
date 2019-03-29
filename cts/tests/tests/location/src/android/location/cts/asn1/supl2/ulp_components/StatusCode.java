/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.location.cts.asn1.supl2.ulp_components;

/*
 */


//
//
import android.location.cts.asn1.base.Asn1Enumerated;
import android.location.cts.asn1.base.Asn1Tag;
import android.location.cts.asn1.base.BitStream;
import android.location.cts.asn1.base.BitStreamReader;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import javax.annotation.Nullable;


/**
 */
public  class StatusCode extends Asn1Enumerated {
  public enum Value implements Asn1Enumerated.Value {
    unspecified(0),
    systemFailure(1),
    unexpectedMessage(2),
    protocolError(3),
    dataMissing(4),
    unexpectedDataValue(5),
    posMethodFailure(6),
    posMethodMismatch(7),
    posProtocolMismatch(8),
    targetSETnotReachable(9),
    versionNotSupported(10),
    resourceShortage(11),
    invalidSessionId(12),
    nonProxyModeNotSupported(13),
    proxyModeNotSupported(14),
    positioningNotPermitted(15),
    authNetFailure(16),
    authSuplinitFailure(17),
    consentDeniedByUser(100),
    consentGrantedByUser(101),
    ;

    Value(int i) {
      value = i;
    }

    private int value;
    public int getAssignedValue() {
      return value;
    }

    @Override public boolean isExtensionValue() {
      return false;
    }
  }

  public enum ExtensionValue implements Asn1Enumerated.Value {
    ver2_incompatibleProtectionLevel(18),
    ver2_serviceNotSupported(19),
    ver2_insufficientInterval(20),
    ver2_noSUPLCoverage(21),
    ver2_sessionStopped(102),
    ;

    ExtensionValue(int i) {
      value = i;
    }

    private int value;
    @Override public int getAssignedValue() {
      return value;
    }

    @Override public boolean isExtensionValue() {
      return true;
    }
  }

  

  private static final Asn1Tag TAG_StatusCode
      = Asn1Tag.fromClassAndNumber(-1, -1);

  public StatusCode() {
    super();
  }

  @Override
  @Nullable
  protected Asn1Tag getTag() {
    return TAG_StatusCode;
  }

  @Override
  protected boolean isTagImplicit() {
    return true;
  }

  public static Collection<Asn1Tag> getPossibleFirstTags() {
    if (TAG_StatusCode != null) {
      return ImmutableList.of(TAG_StatusCode);
    } else {
      return Asn1Enumerated.getPossibleFirstTags();
    }
  }

  @Override protected boolean isExtensible() {
    return true;
  }

  @Override protected Asn1Enumerated.Value lookupValue(int ordinal) {
    return Value.values()[ordinal];
  }

  @Override protected Asn1Enumerated.Value lookupExtensionValue(int ordinal) {
    return ExtensionValue.values()[ordinal];
  }

  @Override protected int getValueCount() {
    return Value.values().length;
  }

  /**
   * Creates a new StatusCode from encoded stream.
   */
  public static StatusCode fromPerUnaligned(byte[] encodedBytes) {
    StatusCode result = new StatusCode();
    result.decodePerUnaligned(new BitStreamReader(encodedBytes));
    return result;
  }

  /**
   * Creates a new StatusCode from encoded stream.
   */
  public static StatusCode fromPerAligned(byte[] encodedBytes) {
    StatusCode result = new StatusCode();
    result.decodePerAligned(new BitStreamReader(encodedBytes));
    return result;
  }

  @Override public Iterable<BitStream> encodePerUnaligned() {
    return super.encodePerUnaligned();
  }

  @Override public Iterable<BitStream> encodePerAligned() {
    return super.encodePerAligned();
  }

  @Override public void decodePerUnaligned(BitStreamReader reader) {
    super.decodePerUnaligned(reader);
  }

  @Override public void decodePerAligned(BitStreamReader reader) {
    super.decodePerAligned(reader);
  }

  @Override public String toString() {
    return toIndentedString("");
  }

  public String toIndentedString(String indent) {
    return "StatusCode = " + getValue() + ";\n";
  }
}
