;; Copies the owner's entries into a table of its own and calls through it.
(module
  (type $i32 (func (result i32)))
  (import "owner" "tab" (table $imported 6 funcref))
  (table $own 6 funcref)

  (func (export "copy")
    (table.copy $own $imported (i32.const 0) (i32.const 0) (i32.const 6)))

  (func (export "call") (param i32) (result i32)
    (call_indirect $own (type $i32) (local.get 0)))
)
