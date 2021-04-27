all: src/TCPEnd.class  
  
src/TCPEnd.class: src/TCPEnd.java
	cd src; javac -g TCPEnd.java

clean:
	find src -type f -name "*.class" -delete
# learned from https://unix.stackexchange.com/questions/352636/remove-all-class-files-from-folders-in-bash