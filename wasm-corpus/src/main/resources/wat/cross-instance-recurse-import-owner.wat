;; Exports a function that recurses until the stack runs out, counting levels in "depth".
(module
  (global (export "depth") (mut i32) (i32.const 0))

  (func $recurse (export "recurse") (result i32)
    (global.set 0 (i32.add (global.get 0) (i32.const 1)))
    (call $recurse))
)
