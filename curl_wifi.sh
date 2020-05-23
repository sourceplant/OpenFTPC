#!/bin/bash
# Control you wifi device with cURL
# Do not get locked out, run from a device connected over LAN, backdoor for your wifi radio
if [ ! "$#" -ne 3 ]; then
    user=${2}
    p_raw=${3}
    #Getting Response Header (-D) and reponse html page to fetch cookie, csrf_param and csrf_token
    header_and_index="$(curl -s 'http://192.168.1.1/' -H 'Connection: keep-alive' -D - | grep -iE 'cookie|csrf' | tr '</>' '\n')"

    #Note: In bash double quotes expansion of variable secures the next line etc..
    req_cookie=$(echo "${header_and_index}" |awk -F';| ' '/Set-Cookie/{print "Cookie: "$2}'  | sed 's/^ //g')
    csrf_param=$(echo "${header_and_index}" |awk  -F '"' '/csrf_param/{print $4}')
    csrf_token=$(echo "${header_and_index}" |awk  -F '"' '/csrf_token/{print $4}')

    #Generating secure password for server side validation using fetched csrf values
    #JSON var b=this.content.get("UserName")+base64Encode(SHA256(this.content.get("Password")))+a.csrf_param+a.csrf_token; SHA256(b)
    # Hardcoded my username and password

    p_sha=$(echo -n "${p_raw}" | sha256sum | cut -d " " -f 1)
    p_sha_b64=$(echo -n ${p_sha} | base64 -w 0)
    password=$(echo -n ${user}${p_sha_b64}${csrf_param}${csrf_token} | sha256sum | cut -d " " -f 1)

    #Prepare post data for login URL
    logincmd="{\"csrf\":{\"csrf_param\":\""${csrf_param}"\",\"csrf_token\":\""${csrf_token}"\"},\"data\":{\"UserName\":\"${user}\",\"Password\":\"${password}\",\"isInstance\":true,\"isDestroyed\":false,\"isDestroying\":false,\"isObserverable\":true}}"

    #Note : new csrf for session after authentication
    #Making Post request user/password and getting new session cookie,csrf param and token from response

    auth_head_index=$(curl -s 'http://192.168.1.1/api/system/user_login' -H 'Connection: keep-alive' -H 'Accept: application/json, text/javascript, */*; q=0.01' -H "${req_cookie}" --data-binary "${logincmd}" -D - )

    cookie=$(echo "${auth_head_index}" | awk -F';| ' '/Set-Cookie/{print "Cookie: "$2";activeMenuID=homenetwork_settings; activeSubmenuID=wlan"}'| sed 's/^ //g')
    csrf_param=$(echo "${auth_head_index}" |awk  -F '"' '/csrf_param/{print $4}')
    csrf_token=$(echo "${auth_head_index}" |awk  -F '"' '/csrf_param/{print $8}')

    if [ "$1" == "down" ]; then

      #Command to Disable 2G Radio
          cmd="{\"csrf\":{\"csrf_param\":\""${csrf_param}"\",\"csrf_token\":\""${csrf_token}"\"},\"data\":{\"Enable5G\":false,\"Enable2G\":false,\"OldEnableMaxPower\":false,\"NeedReboot\":false}}"
        curl -s 'http://192.168.1.1/api/ntwk/wlanradio' -H 'Connection: keep-alive' -H "${cookie}" --data-binary "${cmd}"

        elif [ "$1" == "up" ] ; then
      # Command to Enable 2G Radio
          cmd="{\"csrf\":{\"csrf_param\":\""${csrf_param}"\",\"csrf_token\":\""${csrf_token}"\"},\"data\":{\"Enable5G\":false,\"Enable2G\":true,\"OldEnableMaxPower\":false,\"NeedReboot\":false}}"
      curl -s 'http://192.168.1.1/api/ntwk/wlanradio' -H 'Connection: keep-alive' -H "${cookie}" --data-binary "${cmd}"

    elif [ "$1" == "status" ] ; then
      # Request to get current wlan radio status
      curl -s 'http://192.168.1.1/api/ntwk/wlanradio' -H 'Connection: keep-alive' -H "${cookie}"

    else
       echo "Invalid Option"
    fi
    cmd="{\"csrf\":{\"csrf_param\":\""${csrf_param}"\",\"csrf_token\":\""${csrf_token}"\"}}"
    curl -s 'http://192.168.1.1/api/system/user_logout' -H 'Connection: keep-alive' -H "${cookie}" --data-binary "${cmd}"
else
    echo " curl_wifi status|up|down <username> <password> "
fi

