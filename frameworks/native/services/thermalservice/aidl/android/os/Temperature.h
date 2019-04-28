#ifndef ANDROID_THERMALSERVICE_AIDL_ANDROID_OS_TEMPERATURE_H
#define ANDROID_THERMALSERVICE_AIDL_ANDROID_OS_TEMPERATURE_H

#include <binder/Parcelable.h>

namespace android {
namespace os {

class Temperature : public Parcelable {
 public:

  Temperature();
  Temperature(const float value, const int type);
  ~Temperature() override;

  float getValue() const {return value_;};
  float getType() const {return type_;};

  status_t writeToParcel(Parcel* parcel) const override;
  status_t readFromParcel(const Parcel* parcel) override;

 private:
  // The value of the temperature as a float, or NAN if unknown.
  float value_;
  // The type of the temperature, an enum temperature_type from
  // hardware/thermal.h
  int type_;
};

}  // namespace os
}  // namespace android

#endif   // ANDROID_THERMALSERVICE_AIDL_ANDROID_OS_TEMPERATURE_H
