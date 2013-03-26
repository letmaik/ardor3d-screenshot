# adapted from https://github.com/travis-ci/travis-cookbooks/blob/master/ci_environment/xserver/files/default/etc/init.d/xvfb.sh
# using a depth of x16 leads to subsequent failures in some cases
# see https://bugs.freedesktop.org/show_bug.cgi?id=62742

XVFB=/usr/bin/Xvfb
XVFBARGS=":99 -ac -screen 0 1024x768x24"
PIDFILE=/tmp/cucumber_xvfb_99.pid
case "$1" in
  start)
    echo -n "Starting virtual X frame buffer: Xvfb"
    /sbin/start-stop-daemon --start --quiet --pidfile $PIDFILE --make-pidfile --background --exec $XVFB -- $XVFBARGS
    echo "."
    ;;
  stop)
    echo -n "Stopping virtual X frame buffer: Xvfb"
    /sbin/start-stop-daemon --stop --quiet --pidfile $PIDFILE
    rm -f $PIDFILE
    echo "."
    ;;
  restart)
    $0 stop
    $0 start
    ;;
  *)
  echo "Usage: /etc/init.d/xvfb {start|stop|restart}"
  exit 1
esac
exit 0