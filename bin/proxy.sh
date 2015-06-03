#!/usr/bin/env bash
cd `dirname $0`/..
mydir=`pwd`
root=${mydir%/bin}
export ROOT=${ROOT:-$root}

CLASSPATH=""

for jar in $(ls libs/*.jar); do
  CLASSPATH="$CLASSPATH:$jar"
done

CLASSPATH="$CLASSPATH:$mydir/conf"

mkdir -p logs


start() {
    echo "starting nevecbot..."
    java -Xmx512M -Dlog4j.configuration=file:conf/log4j.properties -cp $CLASSPATH dustinl.proxy.HttpProxyServer&
    if [ $? -eq 0 ]; then
        echo $! > logs/proxy.pid
        echo "proxy started"
        return 0
    else
        echo "proxy start failed!!"
        return 1
    fi
}

stop() {
    echo "killing proxy..."
    kill `cat logs/proxy.pid`
    if [ $? -eq 0 ]; then
        echo "proxy stopped"
        return 0
    fi

    echo "proxy stop failed!!!"
    return 1
}


case $1 in
    "start")
        start
        ;;
    "stop")
        stop
        ;;
    "restart")
        stop
        if [ $? -eq 0 ]; then
            start
        fi
        ;;
    *)
        echo "unknown command"
        exit 1
        ;;
esac
