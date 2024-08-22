#!/usr/bin/env bash

# write your script here
echo "File Janitor, 2024"
echo "Powered by Bash"
echo ""

if [[ -z $1 ]]; then
  echo "Type file-janitor.sh help to see available options"
fi

if [[ $1 == "help" ]]; then
  cat file-janitor-help.txt
fi

if [[ $1 == "list" ]]; then
  if [[ -z $2 ]]; then
    echo "Listing files in the current directory"
    echo ""
    ls -A
  else
    if [[ ! -d $2 && ! -e $2 ]]; then
      echo "$2 is not found"
    elif [[ ! -d $2 ]]; then
      echo "$2 is not a directory"
    else
      echo "Listing files in $2"
      echo ""
      ls -A "$2"
    fi
  fi
fi

if [[ $1 == "report" ]]; then
  if [[ -z $2 ]]; then
      echo "The current directory contains:"
      num_tmp=$(find . -maxdepth 1 -name "*.tmp" -type f | wc -l)
      num_log=$(find . -maxdepth 1 -name "*.log" -type f | wc -l)
      num_py=$(find . -maxdepth 1 -name "*.py" -type f | wc -l)
      b_tmp=$(find . -maxdepth 1 -name "*.tmp" -type f -exec du -cb {} + | grep total | cut -f 1)
      b_log=$(find . -maxdepth 1 -name "*.log" -type f -exec du -cb {} + | grep total | cut -f 1)
      b_py=$(find . -maxdepth 1 -name "*.py" -type f -exec du -cb {} + | grep total | cut -f 1)
      [[ -z $b_tmp ]] && b_tmp=0
      [[ -z $b_log ]] && b_log=0
      [[ -z $b_py ]] && b_py=0
      echo "$num_tmp tmp file(s), with total size of $b_tmp bytes"
      echo "$num_log log file(s), with total size of $b_log bytes"
      echo "$num_py py file(s), with total size of $b_py bytes"
    else
      if [[ ! -d $2 && ! -e $2 ]]; then
        echo "$2 is not found"
      elif [[ ! -d $2 ]]; then
        echo "$2 is not a directory"
      else
        echo "$2 contains:"
        num_tmp=$(find $2 -maxdepth 1  -name "*.tmp" -type f | wc -l)
        num_log=$(find $2 -maxdepth 1  -name "*.log" -type f | wc -l)
        num_py=$(find $2 -maxdepth 1  -name "*.py" -type f | wc -l)
        b_tmp=$(find $2 -maxdepth 1  -name "*.tmp" -type f -exec du -cb {} + | grep total | cut -f 1)
        b_log=$(find $2 -maxdepth 1  -name "*.log" -type f -exec du -cb {} + | grep total | cut -f 1)
        b_py=$(find $2 -maxdepth 1  -name "*.py" -type f -exec du -cb {} + | grep total | cut -f 1)
        [[ -z $b_tmp ]] && b_tmp=0
        [[ -z $b_log ]] && b_log=0
        [[ -z $b_py ]] && b_py=0
        echo "$num_tmp tmp file(s), with total size of $b_tmp bytes"
        echo "$num_log log file(s), with total size of $b_log bytes"
        echo "$num_py py file(s), with total size of $b_py bytes"
      fi
    fi
fi

if [[ $1 == "clean" ]]; then
  if [[ -z $2 ]]; then
      echo "Cleaning the current directory..."
      num_tmp=$(find . -maxdepth 1 -name "*.tmp" -type f | wc -l)
      num_log=$(find . -maxdepth 1 -name "*.log" -mtime +3 -type f | wc -l)
      num_py=$(find . -maxdepth 1 -name "*.py" -type f | wc -l)
      if [[ -d $2/python_scripts ]]; then
        mv *.py "$2/python_scripts/"
      elif [[ $num_py -eq 0 ]]; then
        pass
      else
        mkdir "python_scripts/"
        mv *.py "python_scripts/"
      fi
      find . -maxdepth 1 -name "*.tmp" -type f -exec rm {} \;
      find . -maxdepth 1 -name "*.log" -type f -mtime +3 -exec rm {} \;
      echo "Deleting old log files...  done! $num_log files have been deleted"
      echo "Deleting temporary files...  done! $num_tmp files have been deleted"
      echo "Moving python files...  done! $num_py files have been moved"
    else
      if [[ ! -d $2 && ! -e $2 ]]; then
        echo "$2 is not found"
      elif [[ ! -d $2 ]]; then
        echo "$2 is not a directory"
      else
      echo "Cleaning $2..."
      num_tmp=$(find $2 -maxdepth 1 -name "*.tmp" -type f | wc -l)
      num_log=$(find $2 -maxdepth 1 -name "*.log" -mtime +3 -type f | wc -l)
      num_py=$(find $2 -maxdepth 1 -name "*.py" -type f | wc -l)
      if [[ -d $2/python_scripts ]]; then
        mv $2/*.py "$2/python_scripts/"
      elif [[ $num_py -eq 0 ]]; then
        pass
      else
        mkdir "$2/python_scripts/"
        mv $2/*.py "$2/python_scripts/"
      fi
      find $2 -maxdepth 1 -name "*.tmp" -type f -exec rm {} \;
      find $2 -maxdepth 1 -name "*.log" -type f -mtime +3 -exec rm {} \;
      echo "Deleting old log files...  done! $num_log files have been deleted"
      echo "Deleting temporary files...  done! $num_tmp files have been deleted"
      echo "Moving python files...  done! $num_py files have been moved"
      fi
    fi
fi

if [[ -n $1 && $1 != "list" && $1 != "help" && $1 != "report" && $1 != "clean" ]]; then
  echo "Type file-janitor.sh help to see available options"
fi
