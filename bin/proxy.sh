#!/usr/bin/env bash
cd `dirname $0`/..
mydir=`pwd`
root=${mydir%/bin}
export ROOT=${ROOT:-$root}

PID_FILE="logs/proxy.pid"

CLASSPATH=""

for jar in $(ls libs/*.jar); do
  CLASSPATH="$CLASSPATH:$jar"
done

CLASSPATH="$CLASSPATH:$mydir/conf"

mkdir -p logs


start() {
    ps ax|grep `cat $PID_FILE`|grep -v grep > /dev/null
    if [ $? -eq 0 ]; then
        echo "already running"
        exit 1
    fi

    echo "starting nevecbot..."
    java -Xmx512M -Dlog4j.configuration=file:conf/log4j.properties -Djava.net.preferIPv4Stack=true -cp $CLASSPATH dustinl.proxy.HttpProxyServer&
    if [ $? -eq 0 ]; then
        echo $! > ${PID_FILE}
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
        echo "proxy.sh start|stop|restart"
        exit 1
        ;;
esac
