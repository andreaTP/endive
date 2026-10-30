;; Owns the functions in its table, which read its global (42) and memory (1000), call its import and trap.
(module
  (import "host" "seven" (func $host (result i32)))
  (memory 1)
  (global $g (mut i32) (i32.const 42))
  (table (export "tab") 6 funcref)
  (data (i32.const 16) "\e8\03\00\00")

  (func $readGlobal (result i32) global.get $g)
  (func $readMemory (result i32) (i32.load (i32.const 16)))
  (func $trap (result i32) unreachable)
  (func $pair (result i32 i32) global.get $g (i32.load (i32.const 16)))
  (func $bumpGlobal (result i32)
    (global.set $g (i32.add (global.get $g) (i32.const 1)))
    global.get $g)

  (elem (i32.const 0) $readGlobal $readMemory $host $trap $pair $bumpGlobal)
)
