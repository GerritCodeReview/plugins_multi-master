// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.multimaster;

public class MultiMasterUtil {
  private static final double PHI = 1.61803398875d;

  /**
   * Calculate the nth element in the Fibonacci sequence
   *
   * @param n
   * @return
   */
  public static int fib(int n) {
    if (n < 0) {
      throw new RuntimeException("Negative index provided.");
    }

    if (n == 0) {
      return 0;
    }

    if (n == 1) {
      return 1;
    }

    int[] arr = new int[n + 1];
    arr[0] = 0;
    arr[1] = 1;
    for (int i = 2; i <= n; i++) {
      arr[i] = arr[i - 1] + arr[i - 2];
    }

    return arr[n];
  }

  /**
   * Calculate the index of the Fibonacci sequence given a value.
   *
   * From http://en.wikipedia.org/wiki/Fibonacci_number#Relation_to_the_golden_ratio
   *
   * @param k
   * @return
   */
  public static int inverseFib(long k) {
    return (int) Math
        .floor(Math.log(k * Math.sqrt(5d)) / Math.log(PHI) + 0.5);
  }

  /**
   * Index of Fibonacci sequence such that \sum_i^n{F_n} = k
   * (This is a naive implementation. It should not be used for performance critical
   * applications). 
   * 
   * @param fib
   * @return
   */
  public static int inverseCumFib(long k) {
    int n = 0;
    int sum = 0;
    while (sum < k) {
      sum += fib(n++);
    }

    return n;
  }
}
