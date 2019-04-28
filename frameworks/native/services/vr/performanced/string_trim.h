#ifndef ANDROID_DVR_PERFORMANCED_STRING_TRIM_H_
#define ANDROID_DVR_PERFORMANCED_STRING_TRIM_H_

#include <functional>
#include <locale>
#include <string>

namespace android {
namespace dvr {

// Trims whitespace from the left side of |subject| and returns the result as a
// new string.
inline std::string LeftTrim(std::string subject) {
  subject.erase(subject.begin(),
                std::find_if(subject.begin(), subject.end(),
                             std::not1(std::ptr_fun<int, int>(std::isspace))));
  return subject;
}

// Trims whitespace from the right side of |subject| and returns the result as a
// new string.
inline std::string RightTrim(std::string subject) {
  subject.erase(std::find_if(subject.rbegin(), subject.rend(),
                             std::not1(std::ptr_fun<int, int>(std::isspace)))
                    .base(),
                subject.end());
  return subject;
}

// Trims whitespace from the both sides of |subject| and returns the result as a
// new string.
inline std::string Trim(std::string subject) {
  subject.erase(subject.begin(),
                std::find_if(subject.begin(), subject.end(),
                             std::not1(std::ptr_fun<int, int>(std::isspace))));
  subject.erase(std::find_if(subject.rbegin(), subject.rend(),
                             std::not1(std::ptr_fun<int, int>(std::isspace)))
                    .base(),
                subject.end());
  return subject;
}

}  // namespace dvr
}  // namespace android

#endif  // ANDROID_DVR_PERFORMANCED_STRING_TRIM_H_
