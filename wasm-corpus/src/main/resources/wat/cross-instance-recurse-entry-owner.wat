;; A table entry that recurses until the stack runs out, counting levels in "depth".
(module
  (global (export "depth") (mut i32) (i32.const 0))
  (table (export "tab") 1 funcref)

  (func $recurse (result i32)
    (global.set 0 (i32.add (global.get 0) (i32.const 1)))
    (call $recurse))

  (elem (i32.const 0) $recurse)
)
