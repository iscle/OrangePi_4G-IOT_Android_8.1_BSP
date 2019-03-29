# HOW TO UPDATE SOURCE.ANDROID.COM #

Googlers, please see: go/sac-guide

The source.android.com site contains tutorials, references, and other
information related to the Android Open Source Project (AOSP). To report an
issue with the documentation on source.android.com, please file a bug at:
https://issuetracker.google.com/issues/new?component=191476

To make updates to the source files themselves, follow the instructions below.

### File Location ###

The source.android.com source files are stored in the platform/docs/source.android.com/
Android project:
https://android.googlesource.com/platform/docs/source.android.com/

The files to be edited are located in: <projroot>/docs/source.android.com/<language-code>/

Subdirectories exist for the tabs of source.android.com with their structure
roughly (but not identically) mirroring navigation of the site. For exceptions,
the contents of the Porting tab can be found in the devices/ subdirectory,
while the contents of the Tuning tab reside in the devices/tech subdirectory.
(This is temporary while navigational changes are underway.)

## Edit Instructions ##

1. Initialize and sync the repository and download the Android source per:
https://source.android.com/source/downloading.html

2. Navigate to the docs/source.android.com project.

3. Start a temporary branch for your changes with a command resembling:
$ repo start <topic-branch-name> .

See the Repo command reference for more details:
http://source.android.com/source/using-repo.html#start

4. Add or edit the file(s) and save your changes:
$ git add <file>
$ git commit
$ repo upload .

5. Iteratively improve the change and amend the commit:
$ git commit -a --amend
$ repo upload .

6. Once satisfied, include the changelist in a bug filed at:
https://issuetracker.google.com/issues/new?component=191476

Your change will be routed to the source.android.com team for evaluation and
inclusion.
