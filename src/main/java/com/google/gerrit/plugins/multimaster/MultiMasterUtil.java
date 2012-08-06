// Copyright (c) 2012, Code Aurora Forum. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following
// disclaimer in the documentation and/or other materials provided
// with the distribution.
// * Neither the name of Code Aurora Forum, Inc. nor the names of its
// contributors may be used to endorse or promote products derived
// from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
// BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
// WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
// OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
// IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

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
