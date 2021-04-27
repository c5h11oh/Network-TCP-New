all: src/TCPEnd.class  
  
src/TCPEnd.class: src/TCPEnd.java
	cd src; javac -g TCPEnd.java
