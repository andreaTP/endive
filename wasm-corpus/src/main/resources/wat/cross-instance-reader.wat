;; Calls through the holder's table; its own function has the writer's index but returns -1.
(module
  (type $i32 (func (result i32)))
  (import "holder" "tab" (table 2 funcref))
  (global $g i32 (i32.const -1))

  (func $own (result i32) global.get $g)

  (func (export "call") (param i32) (result i32)
    (call_indirect (type $i32) (local.get 0)))
)
