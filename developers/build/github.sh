#!/usr/bin/env bash

##############################################################################
##
##  GitHub Upload+Update Script (V2, combined) for DevPlat Samples
##
##############################################################################

update=true
upload=true
deleteTemp=true
useAllSamples=true
allSamples=()
token=

## Generates a random 32 character alphaneumeric string to use as a post script 
##   for the temporary code folder (folder will be deleted at end)
folderPS=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)

#utility function to print to stderr
echoerr() { echo "$@" 1>&2; }

display_usage() {
echo -e "\e[90mUsage:

      -t | --token [github_auth_token]
         Input an auth token to access the googlesamples GitHub org
         (if this is not present, you will be prompted for one later)

      -s | --samples [sample1 sample2 sample3 ... sampleN]
         If you don't want to check the entire samples folder,
         you can specify which samples to use with this option.

      --upload-only
          Only uploads new samples - samples with existing
          repos will be ignored

      --update-only
          Only updates samples with existing repos - new
          samples will be ignored

      --keep-temp-files
          Will not delete the temporary directory used to pull/push
          to Github. (normally deleted upon exit) Preserves logs.

  This script can be run with no options - it will check the entire
  ./prebuilts/gradle folder and prompt for an auth token when needed.\e[0m\n"
}

##############################################################################
##  Make sure we delete the temporary folder (if it gets created) before exiting
finish() {
  if $deleteTemp; then
    if [ -d "../github-temp$folderPS" ]; then
     cd ..
     rm -rf ./github-temp$folderPS
    elif [ -d "github-temp$folderPS" ]; then
     rm -rf ./github-temp$folderPS
    fi
  fi
}
# this ensures finish() will always be called no matter how the script ends
trap finish EXIT


##############################################################################
##  Process input parameters. (see above for usage)

## How this works:
##  $# is the number of parameters passed in
##  $1 is the first parameter, $2 the second, and so on. (space delimited)
##  shift basically left shifts the params array - $1 goes away, $2 becomes $1, etc
##  Thus, this while loop iterates through all command line parameters
while [[ $# > 0 ]]; do
case "$1" in

  -t|--token)
    if [[ $2 != -* ]] && [[ $2 ]]; then
      token="$2"; shift
    else
      echoerr -e "Option $1 requires an argument. Cancelling script.\nUse --help to display usage."
      exit 1
    fi;;

  --update-only) upload=false;;

  --upload-only) update=false;;

  --keep-temp-files) deleteTemp=false;;

  -s|--samples)
    useAllSamples=false
    while [[ $2 != -* ]] && [[ $2 ]]; do
      #if true; then ##for testing
      if [ -d "./prebuilts/gradle/$2" ]; then
        allSamples+=("$2")
        shift
      else
        echoerr -e "Sample \"$2\" does not exist in ./prebuilts/gradle. Cancelling script.\n"
        exit 1
      fi
    done;;

  -h|--help)
      display_usage
      exit 1;;

  *)
    echoerr -e "Unknown Option: $1\nUse --help to display usage."
    exit 1;;

esac
shift
done #ends options while loop

if ! $upload && ! $update; then
  echoerr -e "Do not use both --update-only and --upload-only, no samples will be processed.
  If you want to do both updates and uploads, no flags are needed.
  Use --help to display usage."
  exit 1
fi

##############################################################################
##  Get all folders in prebuilts and stick 'em in an array

if $useAllSamples; then
  allSamples=($(ls ./prebuilts/gradle))
fi

# [@] returns all items in an array, ${#...} counts them
numSamples=${#allSamples[@]}
echo "Running script for $numSamples samples"

##############################################################################
##  Iterate through all the samples and see if there's
##  a repo for them on GitHub already - save results so we only do it once

toUpdate=()
toUpload=()
problemSamples=()
curSample=0

echo -ne "Checking for existence of repos... ($curSample/$numSamples)\r"
for i in ${allSamples[@]};
do
 #echo "$i"
 URL=https://github.com/googlesamples/android-$i
 result=$(curl -o /dev/null --silent --head --write-out '%{http_code}' "$URL")
 #echo "$result $URL"
 if [ "$result" -eq "404" ]; then
   toUpload+=("$i")
 elif [ "$result" -eq "200" ]; then
   toUpdate+=("$i")
 else
   problemSamples+=("$i")
 fi
 curSample=$(($curSample+1))
 echo -ne "Checking for existence of repos... ($curSample/$numSamples)\r"
done #close for loop for existence check
echo ""


##############################################################################
##  For every sample that has a repo already, clone it and diff it against
##  the sample code in our git to see if it needs updating.

if $update; then

needsUpdate=()
curSample=0
numUpdates=${#toUpdate[@]}

##make temporary dir to pull code into - will be deleted upon exit.
mkdir github-temp$folderPS
cd github-temp$folderPS

echo -ne "Checking for out-of-date repos... ($curSample/$numUpdates)\r"
for i in ${toUpdate[@]};
do
 URL=https://github.com/googlesamples/android-$i
 git clone $URL.git &> /dev/null
 if [ -d "android-$i" ]; then
   diffResult=$(diff -r --exclude '*.git' ../prebuilts/gradle/$i/ ./android-$i/)
   #for testing (will show diff in every repo)
   #diffResult=$(diff -r ../prebuilts/gradle/$i/ ./android-$i/)`
   #echo $diffResult
   if [ -n "$diffResult" ]; then
     needsUpdate+=("$i")
   fi
 else
   echoerr "Something went wrong when cloning $i - result directory does not exist.
   Leaving temp files in place for further examination."
   deleteTemp=false;
 fi
 curSample=$(($curSample+1))
 echo -ne "Checking for out-of-date repos... ($curSample/$numUpdates)\r"
done #end of for loop when checking which repos actually need updating
echo ""
fi

echo ""

##############################################################################
##  Display the detected changes to be made and get user confirmation

if $upload; then
  if [ ${#toUpload[@]} -ne 0 ]; then
    echo -e "\n\e[1mNew samples that will be uploaded:\e[0m"
    for i in ${toUpload[@]}; do
     echo -e "\e[32m$i\e[0m"
    done
  else
    upload=false
    echo "Nothing new to upload."
  fi
else
  echo "No uploads - check skipped on user request"
fi

if $update; then
  if [ ${#needsUpdate[@]} -ne 0 ]; then
    echo -e "\n\e[1mSamples that will be updated:\e[0m"
    for i in ${needsUpdate[@]}; do
      echo -e "\e[34m$i\e[0m"
    done
  else
    update=false
    echo "Nothing to update."
  fi
else
  echo "No updates - check skipped on user request"
fi

if [ ${#problemSamples[@]} -ne 0 ]; then
 echoerr "
These repos returned something other than a 404 or 200 result code:"
 for i in ${problemSamples[@]};
 do
  echoerr "$i"
 done
fi

if ! $upload && ! $update; then
 echo -e "\e[1mLooks like everything's up-to-date.\e[0m\n"
else

read -p "
Do you want to continue? [y/n]: " -n 1 -r
echo
# if they type anything but an upper or lower case y, don't proceed.
if [[ $REPLY =~ ^[Yy]$ ]]
then
   #echo "Commencing Github updates"

##############################################################################
##  If the user hasn't supplied a token via parameter, ask now

if ! [ -n "$token" ]
then
 read -p "
Input a valid googlesamples GitHub access token to continue: " -r
 token=$REPLY
fi

##############################################################################
##  Test that token

tokenTest=$(curl -o /dev/null --silent \
  -H "Authorization: token $token" \
  --write-out '%{http_code}' "https://api.github.com/orgs/googlesamples/repos")

if [ "$tokenTest" -eq "200" ]; then


##############################################################################
##  If there's something to update, do the updates
if [ ${#needsUpdate[@]} -ne 0 ] && $update; then
 for i in ${needsUpdate[@]}; do
   echo -e "\nUpdating $i"
   if [ -d "android-$i" ]; then
     rsync -az --delete --exclude '*.git' ../prebuilts/gradle/$i/ ./android-$i/

     cd ./android-$i/

     git config user.name "google-automerger"
     git config user.email automerger@google.com

     git add .
     git status
     git commit -m "Auto-update"

     git remote set-url origin "https://$token@github.com/googlesamples/android-$i.git"
     git push origin master

     #overwrite remote url to not contain auth token
     git remote set-url origin "http://github.com/googlesamples/android-$i.git"

     cd ..
   else
     echoerr "Something went wrong when cloning $i - result directory does not exist.
Leaving temp files in place for further examination."
   deleteTemp=false;
  fi
 done
fi

#moves out of the temp folder, if we're in it.
if [ -d "../github-temp$folderPS" ]; then
   cd ..
fi

##############################################################################
##  If there's something new to upload, do the uploads
if [ ${#toUpload[@]} -ne 0 ] && $upload; then
 for i in ${toUpload[@]}; do
   echo -e "\nUploading $i"

   repoName="googlesamples/android-$i"

   CREATE="curl -H 'Authorization: token '$TOKEN \
     -d '{\"name\":\"android-'$i'\", \"team_id\":889859}' \
     https://api.github.com/orgs/googlesamples/repos"
   eval $CREATE

   #add secondary team permissions (robots)
   ADDTEAM="curl -X PUT \
     -H 'Authorization: token '$TOKEN \
     -H 'Content-Length: 0' \
     https://api.github.com/teams/889856/repos/$repoName"

   eval $ADDTEAM

   URL="https://$token@github.com/$repoName"

   cd $i
   git init
    #overrides .gitconfig just for this project - does not alter your global settings.
   git config user.name "google-automerger"
   git config user.email automerger@google.com
   git add .
   git commit -m "Initial Commit"
   git remote add origin $URL
   git push origin master
   cd ..


 done
fi

else
  echoerr "That token doesn't work. A test returned the code: $tokenTest"
fi

else
   echo "User cancelled Github update."
fi

fi #end of "is there something to do?" if statement