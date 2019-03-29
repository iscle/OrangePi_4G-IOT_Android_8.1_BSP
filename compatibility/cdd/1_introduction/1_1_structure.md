## 1.1 Document Structure

### 1.1.1\. Requirements by Device Type

[Section 2](#2_device_types) contains all the MUST and STRONGLY RECOMMENDED
requirements that apply to a specific device type. Each subsection of
[Section 2](#2_device_types) is dedicated to a specific device type.

All the other requirements, that universally apply to any Android device
implementations, are listed in the sections after [Section 2](#2_device_types).
These requirements are referenced as "Core Requirements" in this document.

### 1.1.2\. Requirement ID

Requirement ID is assigned for MUST requirements.

*    The ID is assigned for MUST requirements only.
*    STRONGLY RECOMMENDED requirements are marked as [SR] but ID is not assigned.
*    The ID consists of : Device Type ID - Condition ID - Requirement ID
     (e.g. C-0-1).

Each ID is defined as below:

*    Device Type ID (see more on [2. Device Types](#2_device_types)
     *    C: Core (Requirements that are applied to any Android device implementations)
     *    H: Android Handheld device
     *    T: Android Television device
     *    A: Android Automotive implementation
*    Condition ID
     *    When the requirement is unconditional, this ID is set as 0.
     *    When the requirement is conditional, 1 is assinged for the 1st
          condition and the number increments by 1 within the same section and
          the same device type.
*    Requirement ID
     *    This ID starts from 1 and increments by 1 within the same section and
          the same condition.