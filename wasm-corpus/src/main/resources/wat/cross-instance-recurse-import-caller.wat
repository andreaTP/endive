;; Descends the given levels, counting them in "depth", then calls the owner's export.
(module
  (import "owner" "recurse" (func $recurse (result i32)))
  (global (export "depth") (mut i32) (i32.const 0))

  (func $descend (export "descend") (param i32) (result i32)
    (global.set 0 (i32.add (global.get 0) (i32.const 1)))
    (if (result i32) (local.get 0)
      (then (call $descend (i32.sub (local.get 0) (i32.const 1))))
      (else (call $recurse))))
)
